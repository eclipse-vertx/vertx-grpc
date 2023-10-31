/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.User;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcException;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.auth.GrpcAuthenticationHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerImpl implements GrpcServer {

  private final Vertx vertx;
  private final GrpcAuthenticationHandler authHandler;
  private Handler<GrpcServerRequest<Buffer, Buffer>> requestHandler;
  private Map<String, MethodCallHandler<?, ?>> methodCallHandlers = new HashMap<>();

  public GrpcServerImpl(Vertx vertx) {
    this(vertx, null);
  }

  public GrpcServerImpl(Vertx vertx, GrpcAuthenticationHandler authHandler) {
    this.vertx = vertx;
    this.authHandler = authHandler;
  }

  @Override
  public void handle(HttpServerRequest httpRequest) {
    GrpcMethodCall methodCall = new GrpcMethodCall(httpRequest.path());
    String fmn = methodCall.fullMethodName();
    MethodCallHandler<?, ?> method = methodCallHandlers.get(fmn);

    if (method != null) {
      handle(method, httpRequest, methodCall);
    } else {
      Handler<GrpcServerRequest<Buffer, Buffer>> handler = requestHandler;
      if (handler != null) {
        GrpcServerRequestImpl<Buffer, Buffer> grpcRequest = new GrpcServerRequestImpl<>(httpRequest, GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, methodCall);
        grpcRequest.init();
        handler.handle(grpcRequest);
      } else {
        httpRequest.response().setStatusCode(500).end();
      }
    }
  }

  private <Req, Resp> void handle(MethodCallHandler<Req, Resp> method, HttpServerRequest httpRequest, GrpcMethodCall methodCall) {
    GrpcServerRequestImpl<Req, Resp> grpcRequest = new GrpcServerRequestImpl<>(httpRequest, method.messageDecoder, method.messageEncoder, methodCall);
    grpcRequest.init();
    if (method.useAuthHandler) {
      Future<User> fut = authHandler.authenticate(httpRequest, true);

      // Handle authentication failures
      if (fut.failed()) {
        if (fut.cause() instanceof GrpcException) {
          GrpcException ex = (GrpcException) fut.cause();
          GrpcServerResponse<?, ?> response = grpcRequest.response();
          response.status(ex.status()).end();
        } else {
          httpRequest.response().setStatusCode(500).end();
        }
        return;
      } else {
        User user = fut.result();
        if (user != null) {
          grpcRequest.setUser(user);
        }
      }
    }
    method.handle(grpcRequest);
  }

  public GrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    this.requestHandler = handler;
    return this;
  }

  public <Req, Resp> GrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler) {
    return callHandler(methodDesc, handler, false);
  }

  @Override
  public <Req, Resp> GrpcServer authenticatedCallHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler) {
    return callHandler(methodDesc, handler, true);
  }

  private <Req, Resp> GrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler, boolean useAuthHandler) {
    if (handler != null) {
      methodCallHandlers.put(methodDesc.getFullMethodName(), new MethodCallHandler<>(methodDesc, GrpcMessageDecoder.unmarshaller(methodDesc.getRequestMarshaller()), GrpcMessageEncoder.marshaller(methodDesc.getResponseMarshaller()), handler, useAuthHandler));
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
    final boolean useAuthHandler;

    MethodCallHandler(MethodDescriptor<Req, Resp> def, GrpcMessageDecoder<Req> messageDecoder, GrpcMessageEncoder<Resp> messageEncoder, Handler<GrpcServerRequest<Req, Resp>> handler, boolean useAuthHandler) {
      this.def = def;
      this.messageDecoder = messageDecoder;
      this.messageEncoder = messageEncoder;
      this.handler = handler;
      this.useAuthHandler = useAuthHandler;
    }

    @Override
    public void handle(GrpcServerRequest<Req, Resp> grpcRequest) {
      handler.handle(grpcRequest);
    }
  }
}
