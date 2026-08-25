package io.vertx.grpc.server.impl;

import io.vertx.grpc.common.impl.GrpcMessageDeframer;

/**
 * A class representing a gRPC invocation that connects a gRPC server request with a gRPC server response.
 */
public class GrpcInvocation {

  public final GrpcMessageDeframer deframer;
  public final HttpGrpcOutboundStream outboundInvoker;

  public GrpcInvocation(GrpcMessageDeframer deframer, HttpGrpcOutboundStream outboundInvoker) {
    this.deframer = deframer;
    this.outboundInvoker = outboundInvoker;
  }
}
