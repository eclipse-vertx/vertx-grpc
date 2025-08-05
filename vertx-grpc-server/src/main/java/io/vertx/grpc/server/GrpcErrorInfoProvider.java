package io.vertx.grpc.server;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Interface for providing detailed error information in gRPC server responses.
 * <p>
 * Implementing this interface allows exceptions to expose structured gRPC error details,
 * including a status a descriptive error message, and optional trailers.
 * </p>
 * <p>
 * This design enables custom exceptions to propagate meaningful and rich error context to gRPC clients
 * without coupling to a specific exception class.
 * </p>
 */

public interface GrpcErrorInfoProvider {

  /**
   * Returns the GrpcStatus associated with this error.
   *
   * @return the gRPC status
   */
  GrpcStatus status();

  /**
   * Returns the gRPC error message to send to the client.
   *
   * @return the error message as a string
   */
  String message();

  /**
   * Returns optional key-value trailers to include in the response.
   * Can be {@code null} or empty.
   *
   * @return containing error trailers
   */
  MultiMap trailers();
}
