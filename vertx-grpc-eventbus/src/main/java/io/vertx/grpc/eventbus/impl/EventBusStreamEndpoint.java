package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A client or server endpoint that multiplexes all of its streams over a single private address. The consumer on that
 * address is bound at creation, so streams just insert into the map and demux from it, with no lazy registration.
 */
abstract class EventBusStreamEndpoint {

  private final ContextInternal context;
  private final EventBus eventBus;
  private final String address;
  private final ConcurrentMap<Long, FrameHandler> streams = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  private MessageConsumer<Object> consumer;

  EventBusStreamEndpoint(Vertx vertx, EventBus eventBus, String prefix) {
    this.context = (ContextInternal) vertx.getOrCreateContext();
    this.eventBus = eventBus;
    this.address = prefix + UUID.randomUUID();
  }

  ContextInternal context() {
    return context;
  }

  EventBus eventBus() {
    return eventBus;
  }

  String address() {
    return address;
  }

  long register(FrameHandler stream) {
    long id = sequence.incrementAndGet();
    streams.put(id, stream);
    return id;
  }

  void closed(FrameHandler stream) {
    streams.values().remove(stream);
  }

  Future<Void> bind() {
    Promise<Void> promise = context.promise();
    context.runOnContext(v -> {
      consumer = eventBus.consumer(address, this::dispatch);
      consumer.completion().onComplete(promise);
    });
    return promise.future();
  }

  private void dispatch(Message<Object> message) {
    TransportFrame frame = EventBusGrpcCodec.decodeFrame(message);
    FrameHandler handler = streams.get(frame.getStreamId());
    if (handler != null) {
      handler.handle(frame, message);
    }
  }

  Future<Void> closeStreams() {
    List<FrameHandler> active = new ArrayList<>(streams.values());
    streams.clear();
    List<Future<Void>> futures = new ArrayList<>();
    for (FrameHandler stream : active) {
      Promise<Void> promise = Promise.promise();
      stream.close(promise);
      futures.add(promise.future());
    }
    if (consumer != null) {
      futures.add(consumer.unregister());
      consumer = null;
    }
    return Future.all(futures).mapEmpty();
  }
}
