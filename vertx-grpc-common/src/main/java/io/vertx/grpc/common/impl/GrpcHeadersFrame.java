package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;

import java.time.Duration;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcHeadersFrame extends GrpcFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.HEADERS;
  }

  String contentType();
  String encoding();
  MultiMap headers();
  Duration timeout();
}
