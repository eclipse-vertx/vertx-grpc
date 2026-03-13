package io.vertx.grpc.common.impl;

import io.vertx.core.Future;

public interface GrpcInvoker {

  Future<Void> write(GrpcFrame frame);

}
