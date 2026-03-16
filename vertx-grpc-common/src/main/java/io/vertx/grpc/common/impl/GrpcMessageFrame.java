package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.GrpcMessage;

/**
 * A message.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcMessageFrame extends GrpcFrame {

  // TODO : add compressed

  @Override
  default GrpcFrameType type() {
    return GrpcFrameType.MESSAGE;
  }

  /**
   * @return the message
   */
  GrpcMessage message();

}
