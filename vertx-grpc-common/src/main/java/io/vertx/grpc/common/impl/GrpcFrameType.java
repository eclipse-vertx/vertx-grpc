package io.vertx.grpc.common.impl;

public enum GrpcFrameType {

  HEADERS,
  MESSAGE,
  TRAILERS,
  CANCEL,
  OTHER

}
