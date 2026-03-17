package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Signals the trailers, this frame is server to client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcTrailersFrame extends GrpcFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.TRAILERS;
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
   * @return the trailers
   */
  MultiMap trailers();

}
