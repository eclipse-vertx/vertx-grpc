package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.GrpcMessage;

import java.util.Objects;

public class DefaultGrpcMessageFrame implements GrpcMessageFrame {

  private final GrpcMessage message;

  public DefaultGrpcMessageFrame(GrpcMessage message) {
    this.message = Objects.requireNonNull(message);
  }

  @Override
  public GrpcMessage message() {
    return message;
  }
}
