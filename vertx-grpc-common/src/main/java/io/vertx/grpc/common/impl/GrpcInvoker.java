package io.vertx.grpc.common.impl;

import io.vertx.core.Handler;

public interface GrpcInvoker extends GrpcInboundInvoker, GrpcOutboundInvoker {

  @Override
  GrpcInvoker exceptionHandler(Handler<Throwable> handler);

}
