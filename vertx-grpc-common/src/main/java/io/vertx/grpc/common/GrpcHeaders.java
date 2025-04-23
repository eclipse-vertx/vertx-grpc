package io.vertx.grpc.common;

import io.netty.util.AsciiString;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

@VertxGen
public interface GrpcHeaders {

  /**
   * Header for specifying the timeout for a gRPC call.
   * Format is an integer followed by a time unit: 'S', 'M', 'H' (seconds, minutes, hours).
   * Example: "10S" for a 10-second timeout.
   */
  @GenIgnore
  CharSequence GRPC_TIMEOUT = AsciiString.cached("grpc-timeout");

  /**
   * Header indicating the compression algorithm used for the message payload.
   * Common values include "gzip", "snappy", and "identity" (no compression).
   */
  @GenIgnore
  CharSequence GRPC_ENCODING = AsciiString.cached("grpc-encoding");

  /**
   * Header specifying which compression algorithms the client or server supports.
   * Multiple algorithms can be specified as a comma-separated list.
   */
  @GenIgnore
  CharSequence GRPC_ACCEPT_ENCODING = AsciiString.cached("grpc-accept-encoding");

  /**
   * Header specifying the type of the message being transmitted.
   */
  @GenIgnore
  CharSequence GRPC_MESSAGE_TYPE = AsciiString.cached("grpc-message-type");

  /**
   * Header containing the status code of a gRPC response.
   * This is used to communicate error conditions from the server to the client.
   * Values are defined in the gRPC protocol specification.
   */
  @GenIgnore
  CharSequence GRPC_STATUS = AsciiString.cached("grpc-status");
}
