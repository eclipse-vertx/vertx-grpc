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
   * The wire format, carrying the {@link io.vertx.grpc.common.WireFormat#name()} value, e.g. {@code "proto"} or {@code "json"}.
   */
  public static final String WIRE_FORMAT = "grpc-wire-format";

  /**
   * Streaming handshake, client to server: the client's private address for server to client frames.
   */
  public static final String CLIENT_ADDRESS = "grpc-client-address";

  /**
   * Streaming handshake, client to server: the client's id for this call, used to demux server to client frames.
   */
  public static final String CLIENT_STREAM_ID = "grpc-client-stream-id";

  /**
   * Streaming handshake, server to client: the server's private address for client to server frames.
   */
  public static final String SERVER_ADDRESS = "grpc-server-address";

  /**
   * Streaming handshake, server to client: the server's id for this call, used to demux client to server frames.
   */
  public static final String SERVER_STREAM_ID = "grpc-server-stream-id";

  /**
   * Streaming handshake, server to client: the number of messages the server grants the client to send.
   */
  public static final String INITIAL_WINDOW = "grpc-initial-window";

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
