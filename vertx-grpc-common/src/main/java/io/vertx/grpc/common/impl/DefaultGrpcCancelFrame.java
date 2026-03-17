package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.GrpcCancelFrame;

public class DefaultGrpcCancelFrame implements GrpcCancelFrame {

  public static final GrpcCancelFrame INSTANCE = new DefaultGrpcCancelFrame();

  private DefaultGrpcCancelFrame() {
  }
}
