package io.vertx.grpc.common;

import io.vertx.grpc.common.impl.GrpcFrameType;
import io.vertx.grpc.common.impl.GrpcFrame;

/**
 * Signals an impromptu cancellation, usually sent by the client to the server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcCancelFrame extends GrpcFrame {

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.CANCEL;
  }
}
