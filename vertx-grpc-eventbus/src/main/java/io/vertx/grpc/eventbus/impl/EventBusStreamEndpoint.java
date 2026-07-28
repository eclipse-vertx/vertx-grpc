package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.transport.v1alpha.Ping;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

abstract class EventBusStreamEndpoint {

  private final ContextInternal context;
  private final EventBus eventBus;
  private final String address;
  private final WireFormat pingWireFormat;
  private final long pingInterval;
  private final long pingTimeout;
  private final ConcurrentMap<Long, EventBusGrpcStreamBase> streams = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Peer> peers = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();
  private final AtomicLong pingData = new AtomicLong();

  private MessageConsumer<Object> consumer;
  private long livenessTimerId = -1L;
  private boolean stopped;

  EventBusStreamEndpoint(Vertx vertx, EventBus eventBus, String prefix, WireFormat pingWireFormat, long pingInterval, long pingTimeout) {
    this.context = (ContextInternal) vertx.getOrCreateContext();
    this.eventBus = eventBus;
    this.address = prefix + UUID.randomUUID();
    this.pingWireFormat = pingWireFormat;
    this.pingInterval = pingInterval;
    this.pingTimeout = pingTimeout;
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

  long pingInterval() {
    return pingInterval;
  }

  long pingTimeout() {
    return pingTimeout;
  }

  StreamRegistration createStream() {
    return new StreamRegistration(sequence.incrementAndGet());
  }

  Future<Void> bind() {
    Promise<Void> promise = context.promise();
    context.runOnContext(v -> {
      consumer = eventBus.consumer(address, this::dispatch);
      consumer.completion().onComplete(promise);
      scheduleLivenessCheck();
    });
    return promise.future();
  }

  private void scheduleLivenessCheck() {
    if (stopped || livenessTimerId >= 0) {
      return;
    }
    long period = checkPeriod();
    if (period > 0) {
      livenessTimerId = context.setTimer(period, id -> {
        livenessTimerId = -1L;
        long now = System.currentTimeMillis();
        pingPeers();
        reapSilentPeers(now);
        scheduleLivenessCheck();
      });
    }
  }

  private long checkPeriod() {
    long period = pingInterval > 0 ? pingInterval : Long.MAX_VALUE;
    for (Peer peer : peers.values()) {
      if (peer.timeout > 0) {
        period = Math.min(period, Math.max(1, peer.timeout / 2));
      }
    }
    return period == Long.MAX_VALUE ? -1L : period;
  }

  private void pingPeers() {
    if (pingInterval <= 0) {
      return;
    }
    long data = pingData.incrementAndGet();
    for (Peer peer : peers.values()) {
      Ping.Builder ping = Ping.newBuilder().setData(data);
      DeliveryOptions options = new DeliveryOptions()
        .addHeader(EventBusHeaders.WIRE_FORMAT, pingWireFormat.name())
        .addHeader(EventBusHeaders.PEER_ADDRESS, address);
      peer.producer
        .write(EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setPing(ping), pingWireFormat), options)
        .onFailure(cause -> peerDown(peer, cause));
    }
  }

  private void reapSilentPeers(long now) {
    for (Peer peer : peers.values()) {
      if (peer.timeout > 0 && now - peer.lastSeen > peer.timeout) {
        peerDown(peer, new TimeoutException("No ping from peer " + peer.address + " within " + peer.timeout + " ms"));
      }
    }
  }

  private void peerDown(Peer peer, Throwable cause) {
    if (!peers.remove(peer.address, peer)) {
      return;
    }
    peer.producer.close();
    for (Long id : peer.streams) {
      EventBusGrpcStreamBase stream = streams.get(id);
      if (stream != null) {
        stream.handlePeerDown(cause);
      }
    }
  }

  private void dispatch(Message<Object> message) {
    TransportFrame frame = EventBusGrpcCodec.decodeFrame(message);
    if (frame.getFrameCase() == TransportFrame.FrameCase.PING) {
      handlePing(frame.getPing(), message);
      return;
    }
    EventBusGrpcStreamBase stream = streams.get(frame.getStreamId());
    if (stream != null) {
      stream.handle(frame, message);
    }
  }

  private void handlePing(Ping ping, Message<Object> message) {
    String peerAddress = message.headers().get(EventBusHeaders.PEER_ADDRESS);
    if (peerAddress == null) {
      return;
    }
    Peer peer = peers.get(peerAddress);
    if (peer != null) {
      peer.lastSeen = System.currentTimeMillis();
    }
    if (ping.getAck()) {
      return;
    }

    String wireFormatName = message.headers().get(EventBusHeaders.WIRE_FORMAT);
    WireFormat wireFormat = JsonWireFormat.NAME.equals(wireFormatName) ? WireFormat.JSON : WireFormat.PROTOBUF;
    DeliveryOptions options = new DeliveryOptions()
      .addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name())
      .addHeader(EventBusHeaders.PEER_ADDRESS, address);
    Ping.Builder ack = Ping.newBuilder().setData(ping.getData()).setAck(true);
    eventBus.send(peerAddress, EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setPing(ack), wireFormat), options);
  }

  Future<Void> closeStreams() {
    stopped = true;
    if (livenessTimerId >= 0) {
      context.owner().cancelTimer(livenessTimerId);
      livenessTimerId = -1L;
    }
    for (Peer peer : peers.values()) {
      peer.producer.close();
    }
    peers.clear();
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

  private static final class Peer {

    private final String address;
    private final long timeout;
    private final MessageProducer<Object> producer;
    private final Set<Long> streams = ConcurrentHashMap.newKeySet();

    private volatile long lastSeen;

    private Peer(String address, long timeout, MessageProducer<Object> producer, long now) {
      this.address = address;
      this.timeout = timeout;
      this.producer = producer;
      this.lastSeen = now;
    }
  }

  final class StreamRegistration {

    private final long id;

    private Peer peer;

    private StreamRegistration(long id) {
      this.id = id;
    }

    long id() {
      return id;
    }

    void bind(EventBusGrpcStreamBase stream, String peerAddress, long peerTimeout) {
      streams.put(id, stream);
      Peer bound = peers.computeIfAbsent(peerAddress, addr -> new Peer(addr, peerTimeout, eventBus.sender(addr), System.currentTimeMillis()));
      bound.streams.add(id);
      peer = bound;
      scheduleLivenessCheck();
    }

    void unbind() {
      streams.remove(id);
      Peer bound = peer;
      if (bound != null) {
        peer = null;
        bound.streams.remove(id);
        if (bound.streams.isEmpty() && peers.remove(bound.address, bound)) {
          bound.producer.close();
        }
      }
    }
  }
}
