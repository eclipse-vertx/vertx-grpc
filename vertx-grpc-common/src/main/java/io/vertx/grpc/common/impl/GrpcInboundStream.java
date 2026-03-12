package io.vertx.grpc.common.impl;

import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

public interface GrpcInboundStream extends ReadStream<GrpcFrame> {

  @Override
  GrpcInboundStream pause();

  @Override
  GrpcInboundStream resume();

  @Override
  GrpcInboundStream fetch(long amount);

  @Override
  GrpcInboundStream handler(Handler<GrpcFrame> handler);

  @Override
  GrpcInboundStream exceptionHandler(Handler<Throwable> handler);

  @Override
  GrpcInboundStream endHandler(Handler<Void> handler);

}
