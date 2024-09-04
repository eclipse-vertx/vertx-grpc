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
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.MessageSizeOverflowException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpcio.server.GrpcIoServer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerImpl implements GrpcIoServer {

  private final Vertx vertx;
  private final long maxMessageSize;
  private Handler<GrpcServerRequest<Buffer, Buffer>> requestHandler;
  private Map<String, MethodCallHandler<?, ?>> methodCallHandlers = new HashMap<>();

  public GrpcServerImpl(Vertx vertx, GrpcServerOptions options) {
    this.vertx = vertx;
    this.maxMessageSize = options.getMaxMessageSize();
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
        GrpcServerRequest<Buffer, Buffer> grpcRequest = createRequest(httpRequest, GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, methodCall);
        handler.handle(grpcRequest);
      } else {
        httpRequest.response().setStatusCode(500).end();
      }
    }
  }

  private <Req, Resp> void handle(MethodCallHandler<Req, Resp> method, HttpServerRequest httpRequest, GrpcMethodCall methodCall) {
    GrpcServerRequest<Req, Resp> grpcRequest = createRequest(httpRequest, method.def.decoder(), method.def.encoder(), methodCall);
    method.handle(grpcRequest);
  }

  private <Req, Resp>GrpcServerRequest<Req, Resp> createRequest(HttpServerRequest httpRequest, GrpcMessageDecoder<Req> decoder, GrpcMessageEncoder<Resp> encoder, GrpcMethodCall methodCall) {
    GrpcServerRequestImpl<Req, Resp> grpcRequest = new GrpcServerRequestImpl<>(httpRequest, maxMessageSize, decoder, encoder, methodCall);
    grpcRequest.init();
    grpcRequest.invalidMessageHandler(invalidMsg -> {
      if (invalidMsg instanceof MessageSizeOverflowException) {
        grpcRequest.response().status(GrpcStatus.RESOURCE_EXHAUSTED).end();
      } else {
        grpcRequest.response.cancel();
      }
    });
    return grpcRequest;
  }
  @Override
  public <Req, Resp> GrpcIoServer callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (handler != null) {
      methodCallHandlers.put(serviceMethod.fullMethodName(), new MethodCallHandler<>(serviceMethod, handler));
    } else {
      methodCallHandlers.remove(serviceMethod.fullMethodName());
    }
    return this;
  }

  @Override
  public GrpcIoServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    this.requestHandler = handler;
    return this;
  }

  public <Req, Resp> GrpcIoServer callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler) {
    ServiceMethod<Req, Resp> serviceMethod = ServiceMethod.server(
      ServiceName.create(methodDesc.getServiceName()),
      methodDesc.getBareMethodName(),
      GrpcMessageEncoder.marshaller(methodDesc.getResponseMarshaller()),
      GrpcMessageDecoder.unmarshaller(methodDesc.getRequestMarshaller()));
    return callHandler(serviceMethod, handler);
  }

  private static class MethodCallHandler<Req, Resp> implements Handler<GrpcServerRequest<Req, Resp>> {

    final ServiceMethod<Req, Resp> def;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodCallHandler(ServiceMethod<Req, Resp> def, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.def = def;
      this.handler = handler;
    }

    @Override
    public void handle(GrpcServerRequest<Req, Resp> grpcRequest) {
      handler.handle(grpcRequest);
    }
  }
}
