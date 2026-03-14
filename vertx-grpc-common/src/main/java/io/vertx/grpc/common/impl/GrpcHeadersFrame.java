package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;

import java.time.Duration;

public interface GrpcHeadersFrame extends GrpcFrame {

  String contentType();
  String encoding();
  MultiMap headers();
  Duration timeout();
}
