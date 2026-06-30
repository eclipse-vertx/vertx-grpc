package io.vertx.grpc.common;

/**
 * The cardinality of a gRPC service method.
 */
public enum MethodType {

  /**
   * A single request and a single response.
   */
  UNARY,

  /**
   * A stream of requests and a single response.
   */
  CLIENT_STREAMING,

  /**
   * A single request and a stream of responses.
   */
  SERVER_STREAMING,

  /**
   * A stream of requests and a stream of responses.
   */
  BIDI;

  /**
   * @return the method type for the given cardinality on each side
   */
  public static MethodType of(boolean clientStreaming, boolean serverStreaming) {
    if (clientStreaming) {
      return serverStreaming ? BIDI : CLIENT_STREAMING;
    }
    return serverStreaming ? SERVER_STREAMING : UNARY;
  }

  /**
   * @return whether the client sends a stream of requests
   */
  public boolean clientStreaming() {
    return this == CLIENT_STREAMING || this == BIDI;
  }

  /**
   * @return whether the server sends a stream of responses
   */
  public boolean serverStreaming() {
    return this == SERVER_STREAMING || this == BIDI;
  }

  /**
   * @return whether either side streams, i.e. anything other than {@link #UNARY}
   */
  public boolean streaming() {
    return this != UNARY;
  }
}
