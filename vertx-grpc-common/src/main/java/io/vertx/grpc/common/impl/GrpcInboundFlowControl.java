package io.vertx.grpc.common.impl;

public interface GrpcInboundFlowControl {
  void pause();
  void fetch(long amount);
}
