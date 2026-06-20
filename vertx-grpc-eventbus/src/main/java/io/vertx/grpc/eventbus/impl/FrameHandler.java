package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Closeable;
import io.vertx.core.eventbus.Message;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

/**
 * A single multiplexed stream, demuxed from the endpoint's shared private consumer. It receives the stream's transport
 * frames and is {@link Closeable} so the endpoint can terminate it, notifying the peer, when the endpoint shuts down.
 */
interface FrameHandler extends Closeable {

  void handle(TransportFrame frame, Message<Object> message);
}
