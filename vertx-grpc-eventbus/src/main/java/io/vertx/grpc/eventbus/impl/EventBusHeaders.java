package io.vertx.grpc.eventbus.impl;

import io.vertx.core.MultiMap;

import java.util.Map;

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

  /**
   * The prefix for grpc headers among delivery options.
   */
  public static final String HEADER_PREFIX = "__header__.";

  /**
   * The prefix for grpc trailers among delivery options.
   */
  public static final String TRAILER_PREFIX = "__trailer__.";

  /**
   * Prefixed header encoding.
   */
  static void encodeMultiMap(String prefix, MultiMap src, MultiMap dst) {
    for (Map.Entry<String, String> entry : src) {
      dst.set(prefix + entry.getKey(), entry.getValue());
    }
  }

  /**
   * Prefixed header decoding.
   */
  static void decodeMultimap(String prefix, MultiMap src, MultiMap dst) {
    for (Map.Entry<String, String> entry : src) {
      if (entry.getKey().startsWith(prefix)) {
        dst.set(entry.getKey().substring(prefix.length()), entry.getValue());
      }
    }
  }
}
