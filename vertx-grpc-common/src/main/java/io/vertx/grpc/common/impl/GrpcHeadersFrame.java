package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.WireFormat;

import java.time.Duration;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcHeadersFrame extends GrpcFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.HEADERS;
  }

  WireFormat format();
  String encoding();
  MultiMap headers();
  Duration timeout();
}
