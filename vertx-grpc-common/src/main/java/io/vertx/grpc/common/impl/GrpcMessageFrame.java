package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.GrpcMessage;

public interface GrpcMessageFrame extends GrpcFrame {

  // TODO : add compressed

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.MESSAGE;
  }

  GrpcMessage message();

}
