package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcStatus;

public interface GrpcTrailersFrame extends GrpcFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.TRAILERS;
  }

  GrpcStatus status();
  String statusMessage();
  MultiMap trailers();

}
