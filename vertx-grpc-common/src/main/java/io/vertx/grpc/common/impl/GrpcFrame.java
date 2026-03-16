package io.vertx.grpc.common.impl;

/**
 * The base message exchange over a stream.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcFrame {

  /**
   * @return the frame type
   */
  GrpcFrameType type();

}
