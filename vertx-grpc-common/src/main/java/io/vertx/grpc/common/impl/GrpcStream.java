package io.vertx.grpc.common.impl;

import io.vertx.core.Handler;

public interface GrpcStream extends GrpcInboundStream, GrpcOutboundStream {

  @Override
  GrpcStream exceptionHandler(Handler<Throwable> handler);

}
