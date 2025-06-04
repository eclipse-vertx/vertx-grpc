package io.vertx.grpc.server.impl;

/**
 * A class representing a gRPC invocation that connects a gRPC server request with a gRPC server response.
 */
public class GrpcInvocation<Req, Resp> {

  final GrpcServerRequestImpl<Req, Resp> grpcRequest;
  final GrpcServerResponseImpl<Req, Resp> grpcResponse;

  public GrpcInvocation(GrpcServerRequestImpl<Req, Resp> grpcRequest, GrpcServerResponseImpl<Req, Resp> grpcResponse) {
    this.grpcRequest = grpcRequest;
    this.grpcResponse = grpcResponse;
  }
}
