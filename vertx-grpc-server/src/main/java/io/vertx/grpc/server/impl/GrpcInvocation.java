package io.vertx.grpc.server.impl;

import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;

/**
 * A class representing a gRPC invocation that connects a gRPC server request with a gRPC server response.
 */
public class GrpcInvocation {

  public final GrpcMessageDeframer deframer;
  public final HttpGrpcOutboundInvoker outboundInvoker;
  public final GrpcMessageDecoder<?> messageDecoder;

  public GrpcInvocation(GrpcMessageDeframer deframer, HttpGrpcOutboundInvoker outboundInvoker, GrpcMessageDecoder<?> messageDecoder) {
    this.deframer = deframer;
    this.outboundInvoker = outboundInvoker;
    this.messageDecoder = messageDecoder;
  }
}
