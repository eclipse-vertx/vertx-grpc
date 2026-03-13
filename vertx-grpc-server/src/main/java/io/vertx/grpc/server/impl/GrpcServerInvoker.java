package io.vertx.grpc.server.impl;

import io.vertx.core.Future;
import io.vertx.grpc.common.impl.GrpcFrame;

public interface GrpcServerInvoker {

  Future<Void> write(GrpcFrame frame);

}
