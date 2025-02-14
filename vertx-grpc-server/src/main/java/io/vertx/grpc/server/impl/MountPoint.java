package io.vertx.grpc.server.impl;

import io.vertx.core.http.HttpServerRequest;

public interface MountPoint<I, O> {

  GrpcInvocation<I, O> accept(HttpServerRequest request);

}
