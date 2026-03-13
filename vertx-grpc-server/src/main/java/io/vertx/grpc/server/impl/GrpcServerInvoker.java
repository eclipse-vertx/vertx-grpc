package io.vertx.grpc.server.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;

public interface GrpcServerInvoker {

  Future<Void> writeHeaders(String contentType, MultiMap grpcHeaders, GrpcStatus status, String stateMessage, String encoding);
  Future<Void> writeMessage(GrpcMessage message);
  Future<Void> writeTrailers(MultiMap grpcTrailers, GrpcStatus status, String statusMessage);
  Future<Void> writeTrailers(String contentType, String encoding, GrpcStatus status, String statusMessage, MultiMap headers, MultiMap trailers);


}
