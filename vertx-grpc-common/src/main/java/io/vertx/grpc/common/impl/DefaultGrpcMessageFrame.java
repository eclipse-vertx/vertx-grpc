package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.GrpcMessage;

public class DefaultGrpcMessageFrame implements GrpcMessageFrame {

  private final GrpcMessage message;

  public DefaultGrpcMessageFrame(GrpcMessage message) {
    this.message = message;
  }

  @Override
  public GrpcMessage message() {
    return message;
  }
}
