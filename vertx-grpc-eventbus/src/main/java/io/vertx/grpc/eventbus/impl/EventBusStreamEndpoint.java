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

abstract class EventBusStreamEndpoint {

  private final ContextInternal context;
  private final EventBus eventBus;
  private final String address;
  private final long idleTimeout;
  private final long heartbeatInterval;
  private final ConcurrentMap<Long, EventBusGrpcStreamBase> streams = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  private MessageConsumer<Object> consumer;
  private long idleTimerId = -1L;
  private long heartbeatTimerId = -1L;

  EventBusStreamEndpoint(Vertx vertx, EventBus eventBus, String prefix, long idleTimeout, long heartbeatInterval) {
    this.context = (ContextInternal) vertx.getOrCreateContext();
    this.eventBus = eventBus;
    this.address = prefix + UUID.randomUUID();
    this.idleTimeout = idleTimeout;
    this.heartbeatInterval = heartbeatInterval;
  }

  ContextInternal context() {
    return context;
  }

  long idleTimeout() {
    return idleTimeout;
  }

  long heartbeatInterval() {
    return heartbeatInterval;
  }

  EventBus eventBus() {
    return eventBus;
  }

  String address() {
    return address;
  }

  StreamRegistration createStream() {
    return new StreamRegistration(sequence.incrementAndGet());
  }

  Future<Void> bind() {
    Promise<Void> promise = context.promise();
    context.runOnContext(v -> {
      consumer = eventBus.consumer(address, this::dispatch);
      consumer.completion().onComplete(promise);
      if (idleTimeout > 0) {
        idleTimerId = context.setPeriodic(idleTimeout, id -> checkExpired());
      }
      if (heartbeatInterval > 0) {
        heartbeatTimerId = context.setPeriodic(Math.max(1, heartbeatInterval / 2), id -> sendHeartbeats());
      }
    });
    return promise.future();
  }

  private void checkExpired() {
    long now = System.currentTimeMillis();
    for (EventBusGrpcStreamBase stream : streams.values()) {
      if (stream.expired(now)) {
        stream.handleIdleTimeout();
      }
    }
  }

  private void sendHeartbeats() {
    long now = System.currentTimeMillis();
    for (EventBusGrpcStreamBase stream : streams.values()) {
      stream.checkHeartbeat(now);
    }
  }

  private void dispatch(Message<Object> message) {
    TransportFrame frame = EventBusGrpcCodec.decodeFrame(message);
    EventBusGrpcStreamBase stream = streams.get(frame.getStreamId());
    if (stream != null) {
      stream.handle(frame, message);
    }
  }

  Future<Void> closeStreams() {
    if (idleTimerId >= 0) {
      context.owner().cancelTimer(idleTimerId);
      idleTimerId = -1L;
    }
    if (heartbeatTimerId >= 0) {
      context.owner().cancelTimer(heartbeatTimerId);
      heartbeatTimerId = -1L;
    }
    List<EventBusGrpcStreamBase> active = new ArrayList<>(streams.values());
    streams.clear();
    List<Future<Void>> futures = new ArrayList<>();
    for (EventBusGrpcStreamBase stream : active) {
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

  final class StreamRegistration {

    private final long id;

    private StreamRegistration(long id) {
      this.id = id;
    }

    long id() {
      return id;
    }

    void bind(EventBusGrpcStreamBase stream) {
      streams.put(id, stream);
    }

    void unbind() {
      streams.remove(id);
    }
  }
}
