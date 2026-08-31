/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.server.impl;

import io.vertx.core.Closeable;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.common.impl.Http2GrpcMessageDeframer;
import io.vertx.grpc.server.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerImpl implements GrpcServer, Closeable {

  private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

  private final GrpcServerOptions options;
  private Handler<GrpcServerRequest<Buffer, Buffer>> requestHandler;

  private final List<Service> services = new ArrayList<>();
  private final Map<String, List<MethodCallHandler<?, ?>>> methodCallHandlers = new HashMap<>();

  private final List<GrpcHttpInvoker> invokers;
  private Handler<GrpcMethodCall<Buffer, Buffer>> streamHandler;

  private boolean closing;

  public GrpcServerImpl(Vertx vertx, GrpcServerOptions options) {
    ServiceLoader<GrpcHttpInvoker> loader = ServiceLoader.load(GrpcHttpInvoker.class);
    this.invokers = loader.stream().map(ServiceLoader.Provider::get).collect(Collectors.toList());
    this.options = new GrpcServerOptions(Objects.requireNonNull(options, "options is null"));
  }

  @Override
  public void close(Completable<Void> completion) {
    List<Service> toClose;
    synchronized (this) {
      closing = true;
      toClose = new ArrayList<>(services);
      services.clear();
    }
    List<Future<Void>> futures = toClose
      .stream()
      .map(Service::close)
      .collect(Collectors.toList());
    Future
      .all(futures)
      .<Void>mapEmpty()
      .onComplete(completion);
  }

  @Override
  public void handle(HttpServerRequest httpRequest) {
    GrpcServerRequestInspector.RequestInspectionDetails details = GrpcServerRequestInspector.inspect(httpRequest);
    if (details != null) {
      int errorCode = validate(details);
      if (errorCode > 0) {
        httpRequest.response().setStatusCode(errorCode).end();
        return;
      }
    } else {
      log.trace("invalid content-type header " + httpRequest.getHeader(HttpHeaders.CONTENT_TYPE) + ", sending error 415");
      httpRequest.response().setStatusCode(415).end();
      return;
    }

    io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();

    String path = httpRequest.path();
    while (true) {
      List<MethodCallHandler<?, ?>> mchList = methodCallHandlers.get(path);
      if (mchList != null) {
        for (MethodCallHandler<?, ?> mch : mchList) {
          if (mch.handle(path, httpRequest, details.protocol, details.format, context)) {
            return;
          }
        }
      }
      int idx = path.lastIndexOf('/');
      if (idx <= 0) {
        break;
      }
      path = path.substring(0, idx);
    }

    // Generic handling
    MethodCallHandler<Buffer, Buffer> mch = null;
    if (requestHandler != null) {
      mch = new MethodCallHandler<>(httpRequest.path().substring(1), GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, requestHandler::handle);
    } else if (streamHandler != null) {
      mch = new MethodCallHandler<>(httpRequest.path().substring(1), GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, streamHandler::handle);
    }
    if (mch != null && mch.handle(httpRequest.path(), httpRequest, details.protocol, details.format, context)) {
      return;
    }

    String msg = "Method not found: " + httpRequest.path().substring(1);
    HttpServerResponse response = httpRequest.response();
    switch (details.protocol) {
      case HTTP_2:
      case WEB:
      case WEB_TEXT:
        response.setStatusCode(200);
        response.putHeader(HttpHeaders.CONTENT_TYPE, details.protocol.mediaType());
        response.putHeader(GrpcHeaderNames.GRPC_STATUS, GrpcStatus.UNIMPLEMENTED.toString());
        response.putHeader(GrpcHeaderNames.GRPC_MESSAGE, msg);
        response.end();
        break;
      default:
        response
          .setStatusCode(500)
          .end();
        break;
    }
  }

  private int validate(GrpcServerRequestInspector.RequestInspectionDetails details) {
    // Check HTTP version compatibility
    if (!details.protocol.accepts(details.version)) {
      log.trace(details.protocol.mediaType() + " not supported on " + details.version + ", sending error 415");
      return 415;
    }

    // Check config
    if (!options.isProtocolEnabled(details.protocol)) {
      log.trace(details.protocol + " is not supported, sending error 415");
      return 415;
    }

    if (!options.isFormatEnabled(details.format)) {
      log.trace(details.format + " is not supported, sending error 415");
      return 415;
    }

    return -1;
  }

  public <Req, Resp> GrpcServerImpl streamHandler(ServiceMethod<Req, Resp> serviceMethod, Consumer<GrpcMethodCall<Req, Resp>> handler) {
    if (closing) {
      throw new IllegalStateException("Server closed");
    }
    registerMethodCallHandler("/" + serviceMethod.fullMethodName(), new MethodCallHandler<>(serviceMethod, handler));
    return this;
  }

  /**
   * Generic internal stream handler.
   *
   * @param handler the method call
   * @return this
   */
  public GrpcServerImpl streamHandler(Handler<GrpcMethodCall<Buffer, Buffer>> handler) {
    if (closing) {
      throw new IllegalStateException("Server closed");
    }
    this.streamHandler = handler;
    return this;
  }

  public synchronized GrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    if (closing) {
      throw new IllegalStateException("Server closed");
    }
    this.requestHandler = handler;
    return this;
  }

  private <Req, Resp> void registerMethodCallHandler(String path, MethodCallHandler<Req, Resp> mch) {
    methodCallHandlers.computeIfAbsent(path, k -> new ArrayList<>()).add(mch);
  }

  private <Req, Resp> void unregisterMethodCallHandler(String path, ServiceMethod<Req, Resp> serviceMethod) {
    methodCallHandlers.computeIfPresent(path, (p, registrations) -> {
      registrations.removeIf(mch -> {
        if (mch instanceof ServiceMethodCallHandler<?, ?>) {
          ServiceMethodCallHandler<?, ?> smch = (ServiceMethodCallHandler<?, ?>)mch;
          return smch.method.equals(serviceMethod);
        } else {
          return false;
        }
      });
      return registrations.isEmpty() ? null : registrations;
    });
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <Req, Resp> GrpcServer callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (closing) {
      throw new IllegalStateException("Server closed");
    }
    if (handler != null) {
      ServiceMethodCallHandler<Req, Resp> p = new ServiceMethodCallHandler<>(serviceMethod, handler::handle);
      if (serviceMethod instanceof MountPoint) {
        MountPoint<Req, Resp> mountPoint = (MountPoint<Req, Resp>) serviceMethod;
        List<String> paths = mountPoint.paths();
        for (String path : paths) {
          registerMethodCallHandler(path, p);
        }
      }
      registerMethodCallHandler("/" + serviceMethod.fullMethodName(), p);
    } else {
      if (serviceMethod instanceof MountPoint) {
        MountPoint<Req, Resp> mountPoint = (MountPoint<Req, Resp>) serviceMethod;
        List<String> paths = mountPoint.paths();
        for (String path : paths) {
          unregisterMethodCallHandler(path, serviceMethod);
        }
      }
      unregisterMethodCallHandler("/" + serviceMethod.fullMethodName(), serviceMethod);
    }
    return this;
  }

  @Override
  public GrpcServer addService(Service service) {
    synchronized (this) {
      if (closing) {
        throw new IllegalStateException("Server closed");
      }
      for (Service s : this.services) {
        if (s.name().equals(service.name())) {
          throw new IllegalStateException("Duplicated name: " + service.name().name());
        }
      }
      if (service instanceof ServerAware) {
        ((ServerAware)service).setServer(this);
      }
      for (ServiceMethod method : service.methods()) {
        Handler handler = service.handler(method);
        registerMethodCallHandler(service.pathOfMethod(method.methodName()), new ServiceMethodCallHandler<Object, Object>(method, handler));
      }

      this.services.add(service);
    }

    return this;
  }

  private class HttpGrpcMethodCall<Req, Resp> extends GrpcMethodCall<Req, Resp> {

    private final ContextInternal context;
    private final HttpConnection connection;

    public HttpGrpcMethodCall(ContextInternal context,
                              HttpConnection connection,
                              String path,
                              GrpcStream stream,
                              GrpcMessageDecoder<Req> messageDecoder,
                              GrpcMessageEncoder<Resp> messageEncoder) {
      super(path, stream, messageDecoder, messageEncoder);
      this.context = context;
      this.connection = connection;
    }
  }

  @Override
  public List<Service> services() {
    return Collections.unmodifiableList(services);
  }

  class MethodCallHandler<Req, Resp> {

    private final String fullMethodName;
    private final GrpcMessageDecoder<Req> messageDecoder;
    private final GrpcMessageEncoder<Resp> messageEncoder;
    private final Consumer<? super HttpGrpcMethodCall<Req, Resp>> handler;

    MethodCallHandler(String fullMethodName,
                      GrpcMessageDecoder<Req> messageDecoder,
                      GrpcMessageEncoder<Resp> messageEncoder,
                      Consumer<? super HttpGrpcMethodCall<Req, Resp>> handler) {
      this.fullMethodName = fullMethodName;
      this.messageDecoder = messageDecoder;
      this.messageEncoder = messageEncoder;
      this.handler = handler;
    }

    MethodCallHandler(String fullMethodName,
                      GrpcMessageDecoder<Req> messageDecoder,
                      GrpcMessageEncoder<Resp> messageEncoder,
                      Handler<GrpcServerRequest<Req, Resp>> invoker) {
      this(fullMethodName, messageDecoder, messageEncoder, new Consumer<>() {
        @Override
        public void accept(HttpGrpcMethodCall<Req, Resp> methodCall) {
          GrpcDispatcher<Req, Resp> dispatcher = new GrpcDispatcher<>(
            methodCall.context,
            methodCall,
            methodCall.connection,
            invoker::handle,
            options.getDeadlinePropagation(),
            options.getScheduleDeadlineAutomatically());
          GrpcStream stream = methodCall.stream();
          stream.handler(dispatcher);
          stream.exceptionHandler(dispatcher::handleException);
          stream.errorHandler(dispatcher::handleError);
          stream.endHandler(dispatcher::handleEnd);
          stream.resume();
        }
      });
    }

    MethodCallHandler(ServiceMethod<Req, Resp> method, Consumer<? super HttpGrpcMethodCall<Req, Resp>> handler) {
      this(method.fullMethodName(), method.decoder(), method.encoder(), handler);
    }

    MethodCallHandler(ServiceMethod<Req, Resp> method, Handler<GrpcServerRequest<Req, Resp>> invoker) {
      this(method.fullMethodName(), method.decoder(), method.encoder(), invoker);
    }

    boolean handle(String path, HttpServerRequest httpRequest, GrpcProtocol protocol,  WireFormat format, ContextInternal context) {
      HttpGrpcMethodCall<Req, Resp> methodCall = handle(context, httpRequest.connection(), path, httpRequest, protocol, format);
      if (methodCall != null) {
        handler.accept(methodCall);
        return true;
      } else {
        return false;
      }
    }

    protected GrpcStream createGrpcStream(GrpcProtocol protocol, HttpServerRequest httpRequest, WireFormat format) {
      WireFormat configured = options.getEnabledFormat(format.name());
      if (configured != null) {
        format = configured;
      }

      String encoding = httpRequest.headers().get(GrpcHeaderNames.GRPC_ENCODING);

      HttpGrpcOutboundStream outboundInvoker;
      switch (protocol) {
        case HTTP_2:
          if (!httpRequest.path().equals("/" + fullMethodName)) {
            return null;
          }
          outboundInvoker = new Http2GrpcOutboundStream(httpRequest, new Http2GrpcMessageDeframer(encoding, format));
          break;
        case WEB:
        case WEB_TEXT:
          if (!httpRequest.path().equals("/" + fullMethodName)) {
            return null;
          }
          GrpcMessageDeframer deframer;
          if (httpRequest.version() != HttpVersion.HTTP_2 && GrpcMediaType.isGrpcWebText(httpRequest.getHeader(CONTENT_TYPE))) {
            deframer  = new TextMessageDeframer();
          } else {
            deframer  = new Http2GrpcMessageDeframer(encoding, format);
          }
          outboundInvoker = new WebGrpcOutboundStream(httpRequest, protocol, deframer);
          break;
        case TRANSCODING:
          return null;
        default:
          throw new AssertionError();
      }
      outboundInvoker.init();
      outboundInvoker.init(httpRequest, options.getMaxMessageSize());
      return outboundInvoker;
    }

    private HttpGrpcMethodCall<Req, Resp> handle(ContextInternal context, HttpConnection connection,  String path, HttpServerRequest httpRequest, GrpcProtocol protocol,  WireFormat format) {
      GrpcStream stream = createGrpcStream(protocol, httpRequest, format);
      if (stream == null) {
        return null;
      } else {
        return new HttpGrpcMethodCall<>(context, connection, path, stream, messageDecoder, messageEncoder);
      }
    }
  }

  class ServiceMethodCallHandler<Req, Resp> extends MethodCallHandler<Req, Resp> {

    private final ServiceMethod<Req, Resp> method;

    ServiceMethodCallHandler(ServiceMethod<Req, Resp> method, Handler<GrpcServerRequest<Req, Resp>> invoker) {
      super(method, invoker);
      this.method = method;
    }

    protected GrpcStream createGrpcStream(GrpcProtocol protocol, HttpServerRequest httpRequest, WireFormat format) {
      WireFormat configured = options.getEnabledFormat(format.name());
      if (configured != null) {
        format = configured;
      }

      HttpGrpcOutboundStream stream;
      switch (protocol) {
        case HTTP_2:
        case WEB:
        case WEB_TEXT:
          return super.createGrpcStream(protocol, httpRequest, format);
        case TRANSCODING:
          GrpcInvocation invocation = null;
          for (GrpcHttpInvoker invoker : invokers) {
            invocation = invoker.accept(httpRequest, method, format);
            if (invocation != null) {
              break;
            }
          }
          if (invocation != null) {
            stream = invocation.outboundInvoker;
            break;
          } else {
            return null;
          }
        default:
          throw new AssertionError();
      }
      stream.init();
      stream.init(httpRequest, options.getMaxMessageSize());
      return stream;
    }
  }
}
