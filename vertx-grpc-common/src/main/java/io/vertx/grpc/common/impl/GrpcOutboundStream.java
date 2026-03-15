package io.vertx.grpc.common.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.streams.WriteStream;

public interface GrpcOutboundStream extends WriteStream<GrpcFrame> {

  @Override
  Future<Void> write(GrpcFrame frame);

  @Override
  Future<Void> end(GrpcFrame frame);

  @Override
  Future<Void> end();

  @Override
  GrpcOutboundStream exceptionHandler(@Nullable Handler<Throwable> handler);

  @Override
  GrpcOutboundStream setWriteQueueMaxSize(int maxSize);

  @Override
  boolean writeQueueFull();

  @Override
  GrpcOutboundStream drainHandler(@Nullable Handler<Void> handler);

}
