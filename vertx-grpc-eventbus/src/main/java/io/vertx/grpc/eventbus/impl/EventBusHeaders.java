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
   * Streaming handshake, client to server: how long in milliseconds the client may go unheard before it is considered gone, the same deadline the client applies to this server, so
   * both sides give the stream up at the same time. Absent when the client does not ping.
   */
  public static final String PING_TIMEOUT = "grpc-ping-timeout";

  /**
   * Ping frames, either direction: the sender's private address, so the receiver knows where to send the ack and which peer to credit for it.
   */
  public static final String PEER_ADDRESS = "grpc-peer-address";

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
