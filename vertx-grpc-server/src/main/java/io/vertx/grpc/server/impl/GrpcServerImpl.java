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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerImpl implements GrpcServer {

  private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("application/grpc(-web(-text)?)?(\\+(json|proto))?");

  private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

  private final GrpcServerOptions options;
  private Handler<GrpcServerRequest<Buffer, Buffer>> requestHandler;

  private final List<Service> services = new ArrayList<>();
  private final Map<String, List<MethodCallHandler<?, ?>>> methodCallHandlers = new HashMap<>();
  private final List<Mount> mounts = new ArrayList<>();

  public GrpcServerImpl(Vertx vertx, GrpcServerOptions options) {
    this.options = new GrpcServerOptions(Objects.requireNonNull(options, "options is null"));

    if (this.options.isReflectionEnabled()) {
      this.callHandler(GrpcServerReflectionHandler.SERVICE_METHOD, new GrpcServerReflectionHandler(this));
    }
  }

  // Internal pojo, the name does not matter much at the moment
  private static class Details {
    final GrpcProtocol protocol;
    final WireFormat format;

    Details(GrpcProtocol protocol, WireFormat format) {
      this.protocol = protocol;
      this.format = format;
    }
  }

  private class Mount<Req, Resp> {
    final MountPoint<Req, Resp> mountPoint;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    Mount(MountPoint<Req, Resp> mountPoint, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.mountPoint = mountPoint;
      this.handler = handler;
    }

    boolean invoke(HttpServerRequest httpRequest) {
      GrpcInvocation<Req, Resp> invocation = mountPoint.accept(httpRequest);
      if (invocation != null) {
        GrpcServerImpl.this.handle(invocation, handler);
        return true;
      } else {
        return false;
      }
    }
  }

  private Details determine(String contentType) {
    WireFormat format;
    GrpcProtocol protocol;
    if (contentType != null) {
      Matcher matcher = CONTENT_TYPE_PATTERN.matcher(contentType);
      if (matcher.matches()) {
        if (matcher.group(1) != null) {
          protocol = matcher.group(2) == null ? GrpcProtocol.WEB : GrpcProtocol.WEB_TEXT;
        } else {
          protocol = GrpcProtocol.HTTP_2;
        }
        if (matcher.group(3) != null) {
          switch (matcher.group(4)) {
            case "proto":
              format = WireFormat.PROTOBUF;
              break;
            case "json":
              format = WireFormat.JSON;
              break;
            default:
              throw new UnsupportedOperationException("Not possible");
          }
        } else {
          format = WireFormat.PROTOBUF;
        }
        return new Details(protocol, format);
      } else {
        if (GrpcProtocol.TRANSCODING.mediaType().equals(contentType)) {
          protocol = GrpcProtocol.TRANSCODING;
          format = WireFormat.JSON;
          return new Details(protocol, format);
        } else {
          return null;
        }
      }
    } else {
      return null;
    }
  }

  @Override
  public void handle(HttpServerRequest httpRequest) {
    String contentType = httpRequest.getHeader(CONTENT_TYPE);
    Details details;
    if (contentType != null && (details = determine(contentType)) != null) {
      int errorCode = validate(httpRequest.version(), details.protocol, details.format);
      if (errorCode > 0) {
        httpRequest.response().setStatusCode(errorCode).end();
        return;
      }
    } else {
      httpRequest.response().setStatusCode(415).end();
      return;
    }

    // Exact name lookup first
    GrpcMethodCall methodCall = new GrpcMethodCall(httpRequest.path());
    MethodCallHandler<?, ?> mch = findMethodCallHandler(methodCall, details.format);
    if (mch != null) {
      handle(mch, httpRequest, methodCall, details.protocol, details.format);
      return;
    }

    // Look at mounts (transcoding)
    for (Mount<?, ?> mount : mounts) {
      if (mount.invoke(httpRequest)) {
        return;
      }
    }

    // Generic handling
    Handler<GrpcServerRequest<Buffer, Buffer>> handler = requestHandler;
    if (handler != null) {
      handle(new MethodCallHandler<>(GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, handler), httpRequest, methodCall, details.protocol, details.format);
    } else {
      httpRequest.response().setStatusCode(500).end();
    }
  }

  private MethodCallHandler<?, ?> findMethodCallHandler(GrpcMethodCall methodCall, WireFormat format) {
    List<MethodCallHandler<?, ?>> methods = methodCallHandlers.get(methodCall.fullMethodName());
    if (methods != null) {
      for (MethodCallHandler<?, ?> method : methods) {
        if (method.messageEncoder.format() == format && method.messageDecoder.format() == format) {
          return method;
        }
      }
    }
    return null;
  }

  private int validate(HttpVersion version, GrpcProtocol protocol, WireFormat format) {
    // Check HTTP version compatibility
    if (!protocol.accepts(version)) {
      log.trace(protocol.name() + " not supported on " + version + ", sending error 415");
      return 415;
    }
    // Check config
    switch (protocol) {
      case WEB:
      case WEB_TEXT:
        if (!options.isGrpcWebEnabled()) {
          log.trace("gRPC-Web is not supported on HTTP/1.1, sending error 415");
          return 415;
        }
        break;
    }
    return -1;
  }

  private <Req, Resp> void handle(GrpcInvocation<Req, Resp> invocation, Handler<GrpcServerRequest<Req, Resp>> handler) {
    handle(invocation.grpcRequest, invocation.grpcResponse, handler);
  }

  private <Req, Resp> void handle(MethodCallHandler<Req, Resp> method, HttpServerRequest httpRequest, GrpcMethodCall methodCall, GrpcProtocol protocol, WireFormat format) {
    io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
    GrpcServerRequestImpl<Req, Resp> grpcRequest;
    GrpcServerResponseImpl<Req, Resp> grpcResponse;
    switch (protocol) {
      case HTTP_2:
        grpcRequest = new Http2GrpcServerRequest<>(
          context,
          protocol,
          format,
          options.getMaxMessageSize(),
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
      default:
        throw new AssertionError();
    }
    handle(grpcRequest, grpcResponse, method);
  }

  private <Req, Resp> void handle(GrpcServerRequestImpl<Req, Resp> grpcRequest,
    GrpcServerResponseImpl<Req, Resp> grpcResponse,
    Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (options.getDeadlinePropagation() && grpcRequest.timeout() > 0L) {
      long deadline = System.currentTimeMillis() + grpcRequest.timeout;
      grpcRequest.context().putLocal(GrpcLocal.CONTEXT_LOCAL_KEY, AccessMode.CONCURRENT, new GrpcLocal(deadline));
    }
    grpcResponse.init();
    grpcRequest.init(grpcResponse, options.getScheduleDeadlineAutomatically());
    grpcRequest.invalidMessageHandler(invalidMsg -> {
      if (invalidMsg instanceof MessageSizeOverflowException) {
        grpcRequest.response().status(GrpcStatus.RESOURCE_EXHAUSTED).end();
      } else {
        grpcResponse.cancel();
      }
    });
    grpcRequest.context().dispatch(grpcRequest, handler);
  }

  public GrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    this.requestHandler = handler;
    return this;
  }

  @Override
  public <Req, Resp> GrpcServer callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (handler != null) {

      if (serviceMethod instanceof MountPoint) {
        MountPoint<Req, Resp> mountPoint = (MountPoint<Req, Resp>) serviceMethod;
        mounts.add(new Mount<>(mountPoint, handler));
        return this;
      }

      MethodCallHandler<Req, Resp> p = new MethodCallHandler<>(serviceMethod.decoder(), serviceMethod.encoder(), handler);
      methodCallHandlers.compute(serviceMethod.fullMethodName(), (key, prev) -> {
        if (prev == null) {
          prev = new ArrayList<>();
        }
        for (int i = 0; i < prev.size(); i++) {
          MethodCallHandler<?, ?> a = prev.get(i);
          if (a.messageDecoder.format() == serviceMethod.decoder().format() && a.messageEncoder.format() == serviceMethod.encoder().format()) {
            prev.set(i, p);
            return prev;
          }
        }
        prev.add(p);
        return prev;
      });
    } else {
      methodCallHandlers.compute(serviceMethod.fullMethodName(), (key, prev) -> {
        if (prev != null) {
          for (int i = 0; i < prev.size(); i++) {
            MethodCallHandler<?, ?> a = prev.get(i);
            if (a.messageDecoder.format() == serviceMethod.decoder().format() && a.messageEncoder.format() == serviceMethod.encoder().format()) {
              prev.remove(i);
              break;
            }
          }
          if (prev.isEmpty()) {
            prev = null;
          }
        }
        return prev;
      });
    }
    return this;
  }

  @Override
  public GrpcServer addService(Service service) {
    for (Service s : this.services) {
      if (s.name().equals(service.name())) {
        throw new IllegalStateException("Duplicated name: " + service.name().name());
      }
    }

    this.services.add(service);
    service.bind(this);

    return this;
  }

  @Override
  public List<Service> getServices() {
    return Collections.unmodifiableList(services);
  }

  private static class MethodCallHandler<Req, Resp> implements Handler<GrpcServerRequest<Req, Resp>> {

    final GrpcMessageDecoder<Req> messageDecoder;
    final GrpcMessageEncoder<Resp> messageEncoder;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodCallHandler(GrpcMessageDecoder<Req> messageDecoder, GrpcMessageEncoder<Resp> messageEncoder, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.messageDecoder = messageDecoder;
      this.messageEncoder = messageEncoder;
      this.handler = handler;
    }

    @Override
    public void handle(GrpcServerRequest<Req, Resp> grpcRequest) {
      handler.handle(grpcRequest);
    }
  }
}
