package io.vertx.grpc.common.impl;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.grpc.common.GrpcError;

public interface GrpcStream extends GrpcInboundStream, GrpcOutboundStream {

  @Override
  GrpcStream exceptionHandler(Handler<Throwable> handler);

  @Fluent
  default GrpcStream errorHandler(Handler<GrpcError> handler) {
    return this;
  }

  default Future<Void> fail(GrpcError error) {
    throw new UnsupportedOperationException();
  }
}
