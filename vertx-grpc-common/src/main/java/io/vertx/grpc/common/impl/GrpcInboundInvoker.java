package io.vertx.grpc.common.impl;

import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

public interface GrpcInboundInvoker extends ReadStream<GrpcFrame> {

  @Override
  GrpcInboundInvoker pause();

  @Override
  GrpcInboundInvoker resume();

  @Override
  GrpcInboundInvoker fetch(long amount);

  @Override
  GrpcInboundInvoker handler(Handler<GrpcFrame> handler);

  @Override
  GrpcInboundInvoker exceptionHandler(Handler<Throwable> handler);

  @Override
  GrpcInboundInvoker endHandler(Handler<Void> handler);

}
