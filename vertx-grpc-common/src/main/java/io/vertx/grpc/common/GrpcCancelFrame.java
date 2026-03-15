package io.vertx.grpc.common;

import io.vertx.grpc.common.impl.GrpcFrameType;
import io.vertx.grpc.common.impl.GrpcFrame;

public interface GrpcCancelFrame extends GrpcFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.CANCEL;
  }
}
