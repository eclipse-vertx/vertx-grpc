package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Closeable;
import io.vertx.core.eventbus.Message;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

interface FrameHandler extends Closeable {
  void handle(TransportFrame frame, Message<Object> message);
}
