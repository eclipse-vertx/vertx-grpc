package io.vertx.grpc.server.impl;

import io.vertx.core.http.HttpServerRequest;

public interface MountPoint {

  GrpcInvocation<?, ?> accept(HttpServerRequest request);


}
