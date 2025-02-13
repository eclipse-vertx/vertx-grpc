package io.vertx.grpc.server.impl;

import io.vertx.core.Handler;
import io.vertx.grpc.server.GrpcServerRequest;

/**
 *
 */
public class GrpcInvocation<Req, Resp> {

  final GrpcServerRequestImpl<Req, Resp> grpcRequest;
  final GrpcServerResponseImpl<Req, Resp> grpcResponse;
  final Handler<GrpcServerRequest<Req, Resp>> handler;

  public GrpcInvocation(GrpcServerRequestImpl<Req, Resp> grpcRequest, GrpcServerResponseImpl<Req, Resp> grpcResponse, Handler<GrpcServerRequest<Req, Resp>> handler) {
    this.grpcRequest = grpcRequest;
    this.grpcResponse = grpcResponse;
    this.handler = handler;
  }
}
