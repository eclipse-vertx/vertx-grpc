package io.vertx.grpc.common;

/**
 * Rpc method cardinality
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public enum MethodCardinality {

  /**
   * Both sides send exactly one message.
   */
  UNARY,

  /**
   * The client sends any number of messages, the server replies with exactly one message.
   */
  CLIENT_STREAMING,

  /**
   * The client sends exactly one message, the server replies with any number of messages.
   */
  SERVER_STREAMING,

  /**
   * Both sides send any number of messages.
   */
  BIDI_STREAMING

}
