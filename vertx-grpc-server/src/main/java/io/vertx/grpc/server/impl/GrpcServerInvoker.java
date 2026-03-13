package io.vertx.grpc.server.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;

public interface GrpcServerInvoker {

  void writeHeaders(
    String contentType,
    MultiMap grpcHeaders,
    boolean trailersOnly,
    GrpcStatus status,
    String stateMessage, String encoding);

  Future<Void> writeHead();

  Future<Void> writeMessage(GrpcMessage message);

  Future<Void> writeEnd(GrpcStatus status);

  void writeTrailers(boolean trailersOnly, MultiMap grpcTrailers, GrpcStatus status, String statusMessage);
}
