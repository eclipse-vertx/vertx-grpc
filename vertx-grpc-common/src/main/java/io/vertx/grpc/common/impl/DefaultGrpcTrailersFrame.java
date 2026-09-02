package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcStatus;

public class DefaultGrpcTrailersFrame implements GrpcTrailersFrame {

  private final GrpcStatus status;
  private final String statusMessage;
  private final MultiMap metadata;

  public DefaultGrpcTrailersFrame(GrpcStatus status, String statusMessage, MultiMap metadata) {
    this.status = status;
    this.statusMessage = statusMessage;
    this.metadata = metadata;
  }

  public GrpcStatus status() {
    return status;
  }

  public String statusMessage() {
    return statusMessage;
  }

  public MultiMap metadata() {
    return metadata;
  }
}
