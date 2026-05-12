package io.vertx.grpc.server.impl;

import io.vertx.grpc.server.GrpcServer;

/**
 * Hook for reflection service.
 */
public interface ServerAware {

  void setServer(GrpcServer server);

}
