package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Promise;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrameOrBuilder;

class FrameWrite {

  final TransportFrameOrBuilder frame;
  final Promise<Void> completion;

  FrameWrite(TransportFrameOrBuilder frame, Promise<Void> completion) {
    this.frame = frame;
    this.completion = completion;
  }
}
