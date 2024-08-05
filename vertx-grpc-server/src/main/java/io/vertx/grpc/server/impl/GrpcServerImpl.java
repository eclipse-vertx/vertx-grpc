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
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;

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
  private final Map<String, List<MethodCallHandler<?, ?>>> methodCallHandlers = new HashMap<>();

  public GrpcServerImpl(Vertx vertx, GrpcServerOptions options) {
    this.options = new GrpcServerOptions(Objects.requireNonNull(options, "options is null"));
  }

  @Override
  public void handle(HttpServerRequest httpRequest) {
    int errorCode = refuseRequest(httpRequest);
    if (errorCode > 0) {
      httpRequest.response().setStatusCode(errorCode).end();
      return;
    }
    WireFormat format ;
    String contentType = httpRequest.getHeader(CONTENT_TYPE);
    GrpcProtocol protocol;
    if (contentType != null) {
      Matcher matcher = CONTENT_TYPE_PATTERN.matcher(contentType);
      if (matcher.matches()) {
        if (matcher.group(1) != null) {
          protocol = matcher.group(2) == null ? GrpcProtocol.WEB : GrpcProtocol.WEB_TEXT;
        } else {
          protocol = GrpcProtocol.HTTP_2;
        }
        if (protocol.isWeb() && !options.isGrpcWebEnabled()) {
          httpRequest.response().setStatusCode(415).end();
          return;
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
      } else {
        httpRequest.response().setStatusCode(415).end();
        return;
      }
    } else {
      httpRequest.response().setStatusCode(415).end();
      return;
    }
    GrpcMethodCall methodCall = new GrpcMethodCall(httpRequest.path());
    String fmn = methodCall.fullMethodName();
    List<MethodCallHandler<?, ?>> methods = methodCallHandlers.get(fmn);
    if (methods != null) {
      for (MethodCallHandler<?, ?> method : methods) {
        if (method.messageEncoder.format() == format && method.messageDecoder.format() == format) {
          handle(method, httpRequest, methodCall, protocol, format);
          return;
        }
      }
    }
    Handler<GrpcServerRequest<Buffer, Buffer>> handler = requestHandler;
    if (handler != null) {
      handle(httpRequest, methodCall, protocol, format, GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, handler);
    } else {
      httpRequest.response().setStatusCode(500).end();
    }
  }

  private int refuseRequest(HttpServerRequest request) {
    if (request.version() != HttpVersion.HTTP_2) {
      if (!options.isGrpcWebEnabled()) {
        log.trace("gRPC-Web is not enabled, sending error 505");
        return 505;
      }
      if (!GrpcMediaType.isGrpcWeb(request.headers().get(CONTENT_TYPE))) {
        log.trace("gRPC-Web is the only media type supported on HTTP/1.1, sending error 415");
        return 415;
      }
    }
    return -1;
  }

  private <Req, Resp> void handle(MethodCallHandler<Req, Resp> method, HttpServerRequest httpRequest, GrpcMethodCall methodCall, GrpcProtocol protocol, WireFormat format) {
    handle(httpRequest, methodCall, protocol, format, method.messageDecoder, method.messageEncoder, method);
  }

  private <Req, Resp> void handle(HttpServerRequest httpRequest,
                                  GrpcMethodCall methodCall,
                                  GrpcProtocol protocol,
                                  WireFormat format,
                                  GrpcMessageDecoder<Req> messageDecoder,
                                  GrpcMessageEncoder<Resp> messageEncoder,
                                  Handler<GrpcServerRequest<Req, Resp>> handler) {
    io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
    GrpcServerRequestImpl<Req, Resp> grpcRequest = new GrpcServerRequestImpl<>(
      context,
      options.getScheduleDeadlineAutomatically(),
      protocol,
      format,
      httpRequest,
      messageDecoder,
      messageEncoder,
      methodCall);
    if (options.getDeadlinePropagation() && grpcRequest.timeout() > 0L) {
      long deadline = System.currentTimeMillis() + grpcRequest.timeout;
      context.putLocal(GrpcLocal.CONTEXT_LOCAL_KEY, AccessMode.CONCURRENT, new GrpcLocal(deadline));
    }
    grpcRequest.init(grpcRequest.response);
    context.dispatch(grpcRequest, handler);
  }

  public GrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    this.requestHandler = handler;
    return this;
  }

  @Override
  public <Req, Resp> GrpcServer callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (handler != null) {
      MethodCallHandler<Req, Resp> p = new MethodCallHandler<>(serviceMethod.decoder(), serviceMethod.encoder(), handler);
      methodCallHandlers.compute(serviceMethod.fullMethodName(), (key, prev) -> {
        if (prev == null) {
          prev = new ArrayList<>();
        }
        for (int i = 0;i < prev.size();i++) {
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
          for (int i = 0;i < prev.size();i++) {
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
