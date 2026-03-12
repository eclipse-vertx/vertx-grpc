package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.WireFormat;

import java.time.Duration;

/**
 * Explicitly begin the stream, this triggers beginning the stream.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcHeadersFrame extends GrpcFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.HEADERS;
  }

  /**
   * @return the stream wire format
   */
  WireFormat format();

  /**
   * @return the stream encoding
   */
  String encoding();

  /**
   * @return the headers
   */
  MultiMap headers();

  /**
   * @return an optional timeout
   */
  Duration timeout();
}
