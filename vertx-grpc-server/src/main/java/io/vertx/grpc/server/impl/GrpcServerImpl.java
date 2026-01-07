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
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerImpl implements GrpcServer, Closeable {

  private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("application/grpc(-web(-text)?)?(\\+(json|proto))?");

  private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

  private final GrpcServerOptions options;
  private Handler<GrpcServerRequest<Buffer, Buffer>> requestHandler;

  private final List<Service> services = new ArrayList<>();
  private final Map<String, List<MethodCallHandler<?, ?>>> methodCallHandlers = new HashMap<>();

  private final List<GrpcHttpInvoker> invokers;

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

    GrpcMethodCall methodCall = new GrpcMethodCall(httpRequest.path());
    String path = httpRequest.path();
    while (true) {
      List<MethodCallHandler<?, ?>> mchList = methodCallHandlers.get(path);
      if (mchList != null) {
        for (MethodCallHandler<?, ?> mch : mchList) {
          if (handle(mch, httpRequest, methodCall, details.protocol, details.format)) {
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
    Handler<GrpcServerRequest<Buffer, Buffer>> handler = requestHandler;
    if (handler != null) {
      handle(new MethodCallHandler<>(null, GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, handler), httpRequest, methodCall, details.protocol, details.format);
    } else {
      String msg = "Method not found: " + httpRequest.path().substring(1);
      HttpServerResponse response = httpRequest.response();
      boolean webText = true;
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

    return -1;
  }

  private <Req, Resp> boolean handle(MethodCallHandler<Req, Resp> method, HttpServerRequest httpRequest, GrpcMethodCall methodCall, GrpcProtocol protocol, WireFormat format) {
    io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();

    GrpcServerRequestImpl<Req, Resp> grpcRequest;
    GrpcServerResponseImpl<Req, Resp> grpcResponse;
    switch (protocol) {
      case HTTP_2:
        if (method.method != null && !httpRequest.path().equals("/" + method.method.fullMethodName())) {
          return false;
        }
        grpcRequest = new Http2GrpcServerRequest<>(
          context,
          protocol,
          format,
          httpRequest,
          method.messageDecoder,
          methodCall);
        grpcResponse = new Http2GrpcServerResponse<>(
          context,
          grpcRequest,
          protocol,
          httpRequest.response(),
          method.messageEncoder);
        break;
      case WEB:
      case WEB_TEXT:
        if (method.method != null && !httpRequest.path().equals("/" + method.method.fullMethodName())) {
          return false;
        }
        grpcRequest = new WebGrpcServerRequest<>(
          context,
          protocol,
          format,
          options.getMaxMessageSize(),
          httpRequest,
          method.messageDecoder,
          methodCall);
        grpcResponse = new WebGrpcServerResponse<>(
          context,
          grpcRequest,
          protocol,
          httpRequest.response(),
          method.messageEncoder);
        break;
      case TRANSCODING:
        grpcRequest = null;
        grpcResponse = null;
        for (GrpcHttpInvoker invoker : invokers) {
          GrpcInvocation<Req, Resp> invocation = invoker.accept(httpRequest, method.method);
          if (invocation != null) {
            grpcRequest = invocation.grpcRequest;
            grpcResponse = invocation.grpcResponse;
            break;
          }
        }
        break;
      default:
        throw new AssertionError();
    }
    if (grpcRequest == null || grpcResponse == null) {
      return false;
    }
    grpcResponse.format(format);
    handle(grpcRequest, grpcResponse, method);
    return true;
  }

  private <Req, Resp> void handle(GrpcServerRequestImpl<Req, Resp> grpcRequest,
                                  GrpcServerResponseImpl<Req, Resp> grpcResponse,
                                  Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (options.getDeadlinePropagation() && grpcRequest.timeout() > 0L) {
      long deadline = System.currentTimeMillis() + grpcRequest.timeout;
      grpcRequest.context().putLocal(GrpcLocal.CONTEXT_LOCAL_KEY, AccessMode.CONCURRENT, new GrpcLocal(deadline));
    }
    grpcResponse.init();
    grpcRequest.init(grpcResponse, options.getScheduleDeadlineAutomatically(), options.getMaxMessageSize());
    grpcRequest.invalidMessageHandler(invalidMsg -> {
      if (invalidMsg instanceof MessageSizeOverflowException) {
        grpcRequest.response().status(GrpcStatus.RESOURCE_EXHAUSTED).end();
      } else {
        grpcResponse.cancel();
      }
    });
    grpcRequest.context().dispatch(grpcRequest, handler);
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
      registrations.removeIf(mch -> mch.method.equals(serviceMethod));
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
      MethodCallHandler<Req, Resp> p = new MethodCallHandler<>(serviceMethod, serviceMethod.decoder(), serviceMethod.encoder(), handler);
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

      this.services.add(service);
    }
    service.bind(this);

    return this;
  }

  @Override
  public List<Service> services() {
    return Collections.unmodifiableList(services);
  }

  private static class MethodCallHandler<Req, Resp> implements Handler<GrpcServerRequest<Req, Resp>> {

    final ServiceMethod<Req, Resp> method;
    final GrpcMessageDecoder<Req> messageDecoder;
    final GrpcMessageEncoder<Resp> messageEncoder;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodCallHandler(ServiceMethod<Req, Resp> method, GrpcMessageDecoder<Req> messageDecoder, GrpcMessageEncoder<Resp> messageEncoder, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.method = method;
      this.messageDecoder = messageDecoder;
      this.messageEncoder = messageEncoder;
      this.handler = handler;
    }

    @Override
    public void handle(GrpcServerRequest<Req, Resp> grpcRequest) {
      try {
        handler.handle(grpcRequest);
      } catch (Exception e) {
        grpcRequest.response().fail(e);
      }
    }
  }
}
