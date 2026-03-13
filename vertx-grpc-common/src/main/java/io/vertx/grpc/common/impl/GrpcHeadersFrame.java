package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;

public interface GrpcHeadersFrame extends GrpcFrame {
  String contentType();
  String encoding();
  MultiMap headers();
}
