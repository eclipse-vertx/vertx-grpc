package io.vertx.grpc.common.impl;

/**
 * The base message exchange over an invoker.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface GrpcFrame {

  GrpcFrameType type();

}
