package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Signals the response trailers, this frame is server to client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcTrailersFrame extends GrpcHalfCloseFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.HALF_CLOSE;
  }

  /**
   * @return the status
   */
  GrpcStatus status();

  /**
   * @return the status message
   */
  String statusMessage();

  /**
   * @return the trailers metadata
   */
  MultiMap metadata();

}
