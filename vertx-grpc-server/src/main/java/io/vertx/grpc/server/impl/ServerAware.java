package io.vertx.grpc.server.impl;

import io.vertx.grpc.server.ServiceContainer;

/**
 * Hook for reflection service.
 */
public interface ServerAware {

  void setServer(ServiceContainer server);

}
