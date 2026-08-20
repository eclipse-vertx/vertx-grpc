package io.vertx.grpc.common.impl;

/**
 * Default implementation of {@link GrpcHalfCloseFrame}
 */
public class DefaultGrpcHalfCloseFrame implements GrpcHalfCloseFrame {

  public static DefaultGrpcHalfCloseFrame INSTANCE = new DefaultGrpcHalfCloseFrame();

  private DefaultGrpcHalfCloseFrame() {
  }

  @Override
  public GrpcFrameType type() {
    return GrpcFrameType.HALF_CLOSE;
  }
}
