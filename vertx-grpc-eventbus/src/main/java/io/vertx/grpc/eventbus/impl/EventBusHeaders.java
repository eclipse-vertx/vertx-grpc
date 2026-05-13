package io.vertx.grpc.eventbus.impl;

/**
 * Header names used by the EventBus gRPC transport.
 */
public final class EventBusHeaders {

  /**
   * The gRPC method name, e.g. {@code "SayHello"}.
   */
  public static final String ACTION = "action";

  /**
   * The wire format, carrying the {@link io.vertx.grpc.common.WireFormat} enum name, e.g. {@code "PROTOBUF"} or {@code "JSON"}.
   */
  public static final String WIRE_FORMAT = "grpc-wire-format";

  private EventBusHeaders() {
  }
}
