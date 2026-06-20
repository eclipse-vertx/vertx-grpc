package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A client or server endpoint that multiplexes all of its streams over a single private address. Each stream is
 * assigned an id, demuxed from the endpoint's shared consumer by {@link #dispatch}.
 */
abstract class EventBusStreamEndpoint {

  private final EventBus eventBus;
  private final String address;
  private final ConcurrentMap<Long, FrameHandler> streams = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  private MessageConsumer<Object> consumer;
  private Future<Void> registration;

  EventBusStreamEndpoint(EventBus eventBus, String prefix) {
    this.eventBus = eventBus;
    this.address = prefix + UUID.randomUUID();
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

  synchronized Future<Void> ready() {
    if (registration == null) {
      consumer = eventBus.consumer(address, this::dispatch);
      registration = consumer.completion();
    }
    return registration;
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
      registration = null;
    }
    return Future.all(futures).mapEmpty();
  }
}
