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

import io.grpc.MethodDescriptor;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.impl.GrpcRequestLocal;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerImpl implements GrpcServer {

  private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

  private final GrpcServerOptions options;
  private Handler<GrpcServerRequest<Buffer, Buffer>> requestHandler;
  private Map<String, MethodCallHandler<?, ?>> methodCallHandlers = new HashMap<>();

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
    GrpcMethodCall methodCall = new GrpcMethodCall(httpRequest.path());
    String fmn = methodCall.fullMethodName();
    MethodCallHandler<?, ?> method = methodCallHandlers.get(fmn);
    if (method != null) {
      handle(method, httpRequest, methodCall);
    } else {
      Handler<GrpcServerRequest<Buffer, Buffer>> handler = requestHandler;
      if (handler != null) {
        handle(httpRequest, methodCall, GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, handler);
      } else {
        httpRequest.response().setStatusCode(500).end();
      }
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

  private <Req, Resp> void handle(MethodCallHandler<Req, Resp> method, HttpServerRequest httpRequest, GrpcMethodCall methodCall) {
    handle(httpRequest, methodCall, method.messageDecoder, method.messageEncoder, method);
  }

  private <Req, Resp> void handle(HttpServerRequest httpRequest,
                                  GrpcMethodCall methodCall,
                                  GrpcMessageDecoder<Req> messageDecoder,
                                  GrpcMessageEncoder<Resp> messageEncoder,
                                  Handler<GrpcServerRequest<Req, Resp>> handler) {
    io.vertx.core.internal.ContextInternal context = (ContextInternal) ((HttpServerRequestInternal) httpRequest).context();
    GrpcServerRequestImpl<Req, Resp> grpcRequest = new GrpcServerRequestImpl<>(context, options.getScheduleDeadlineAutomatically(),
      httpRequest, messageDecoder, messageEncoder, methodCall);
    if (options.getDeadlinePropagation() && grpcRequest.timeout() > 0L) {
      long deadline = System.currentTimeMillis() + grpcRequest.timeout;
      context.putLocal(GrpcRequestLocal.CONTEXT_LOCAL_KEY, AccessMode.CONCURRENT, new GrpcRequestLocal(deadline));
    }
    grpcRequest.init();
    context.dispatch(grpcRequest, handler);
  }

  public GrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    this.requestHandler = handler;
    return this;
  }

  public <Req, Resp> GrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (handler != null) {
      methodCallHandlers.put(methodDesc.getFullMethodName(), new MethodCallHandler<>(methodDesc, GrpcMessageDecoder.unmarshaller(methodDesc.getRequestMarshaller()), GrpcMessageEncoder.marshaller(methodDesc.getResponseMarshaller()), handler));
    } else {
      methodCallHandlers.remove(methodDesc.getFullMethodName());
    }
    return this;
  }

  private static class MethodCallHandler<Req, Resp> implements Handler<GrpcServerRequest<Req, Resp>> {

    final MethodDescriptor<Req, Resp> def;
    final GrpcMessageDecoder<Req> messageDecoder;
    final GrpcMessageEncoder<Resp> messageEncoder;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodCallHandler(MethodDescriptor<Req, Resp> def, GrpcMessageDecoder<Req> messageDecoder, GrpcMessageEncoder<Resp> messageEncoder, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.def = def;
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
