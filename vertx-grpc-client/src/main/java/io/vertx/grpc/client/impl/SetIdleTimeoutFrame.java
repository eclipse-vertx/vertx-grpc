package io.vertx.grpc.client.impl;

import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcFrameType;

import java.time.Duration;

public class SetIdleTimeoutFrame implements GrpcFrame {

  private final Duration timeout;

  public SetIdleTimeoutFrame(Duration timeout) {
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("Timeout must be > 0");
    }
    this.timeout = timeout;
  }

  @Override
  public GrpcFrameType type() {
    return GrpcFrameType.OTHER;
  }

  public Duration timeout() {
    return timeout;
  }
}
