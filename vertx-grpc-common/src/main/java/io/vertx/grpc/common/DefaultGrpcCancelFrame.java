package io.vertx.grpc.common;

public class DefaultGrpcCancelFrame implements GrpcCancelFrame {

  public static final GrpcCancelFrame INSTANCE = new DefaultGrpcCancelFrame();

  private DefaultGrpcCancelFrame() {
  }
}
