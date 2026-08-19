package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

/**
 *
 */
class MessageWrite {

  final Promise<Void> completion;
  final TransportFrame.Builder frame;
  final DeliveryOptions deliveryOptions;

  public MessageWrite(Promise<Void> completion, TransportFrame.Builder frame, DeliveryOptions deliveryOptions) {
    this.completion = completion;
    this.frame = frame;
    this.deliveryOptions = deliveryOptions;
  }
}
