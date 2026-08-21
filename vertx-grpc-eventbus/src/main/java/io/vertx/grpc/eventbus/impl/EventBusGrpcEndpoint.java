package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.eventbus.EventBusInternal;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.transport.v1alpha.Cancel;
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

abstract class EventBusGrpcEndpoint {

  protected final ContextInternal producerContext;
  protected final VertxInternal vertx;
  private final EventBusInternal eventBus;
  private final String address;
  private final int id;
  private final WireFormat pingWireFormat;
  private final long pingInterval;
  private final long pingTimeout;
  private final ConcurrentMap<Long, EventBusGrpcStreamBase> streams = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, RemoteEndpoint> remoteEndpoints = new ConcurrentHashMap<>();
  private final AtomicLong pingData = new AtomicLong();
  protected final int initialWindowSize;

  private MessageConsumer<Object> consumer;
  private long livenessTimerId = -1L;
  private boolean stopped;

  EventBusGrpcEndpoint(ContextInternal producerContext, String prefix, WireFormat pingWireFormat, long pingInterval,
                       long pingTimeout, int initialWindowSize) {

    UUID uuid = UUID.randomUUID();

    this.vertx = producerContext.owner();
    this.producerContext = producerContext;
    this.eventBus = vertx.eventBus();
    this.id = uuid.hashCode();
    this.address = prefix + uuid;
    this.pingWireFormat = pingWireFormat;
    this.pingInterval = pingInterval;
    this.pingTimeout = pingTimeout;
    this.initialWindowSize = initialWindowSize;
  }

  int id() {
    return id;
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

  StreamRegistration createStream(long id) {
    return new StreamRegistration(id);
  }

  void bind(Promise<Void> promise) {
    consumer = consumer(address, this::dispatch);
    consumer
      .completion()
      .andThen(ar -> {
        if (ar.succeeded()) {
          scheduleLivenessCheck();
        }
      }).onComplete(promise);
  }

  private void scheduleLivenessCheck() {
    if (stopped || livenessTimerId >= 0) {
      return;
    }
    long period = checkPeriod();
    if (period > 0) {
      livenessTimerId = producerContext.setTimer(period, id -> {
        livenessTimerId = -1L;
        long now = System.currentTimeMillis();
        pingRemoteEndpoints();
        reapSilentRemoteEndpoints(now);
        scheduleLivenessCheck();
      });
    }
  }

  private long checkPeriod() {
    long period = pingInterval > 0 ? pingInterval : Long.MAX_VALUE;
    for (RemoteEndpoint remoteEndpoint : remoteEndpoints.values()) {
      if (remoteEndpoint.timeout > 0) {
        period = Math.min(period, Math.max(1, remoteEndpoint.timeout / 2));
      }
    }
    return period == Long.MAX_VALUE ? -1L : period;
  }

  Future<Message<Object>> request(ContextInternal context, String address, Object body, DeliveryOptions options) {
    return eventBus.request(context, address, body, options);
  }

  MessageConsumer<Object> consumer(String address, Handler<Message<Object>> handler) {
    MessageConsumer<Object> consumer = eventBus.consumer(producerContext, new MessageConsumerOptions().setAddress(address));
    consumer.handler(handler);
    return consumer;
  }

  private void pingRemoteEndpoints() {
    if (pingInterval <= 0) {
      return;
    }
    long data = pingData.incrementAndGet();
    for (RemoteEndpoint remoteEndpoint : remoteEndpoints.values()) {
      Ping.Builder ping = Ping.newBuilder().setData(data);
      DeliveryOptions options = new DeliveryOptions()
        .addHeader(EventBusHeaders.WIRE_FORMAT, pingWireFormat.name())
        .addHeader(EventBusHeaders.REMOTE_ENDPOINT_ADDRESS, address);
      remoteEndpoint.producer
        .write(EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setPing(ping), pingWireFormat), options)
        .onFailure(cause -> remoteEndpointDown(remoteEndpoint, false, cause));
    }
  }

  private void reapSilentRemoteEndpoints(long now) {
    for (RemoteEndpoint remoteEndpoint : remoteEndpoints.values()) {
      if (remoteEndpoint.timeout > 0 && now - remoteEndpoint.lastSeen > remoteEndpoint.timeout) {
        remoteEndpointDown(remoteEndpoint, true, new TimeoutException("No ping from remote endpoint " + remoteEndpoint.address + " within " + remoteEndpoint.timeout + " ms"));
      }
    }
  }

  private void remoteEndpointDown(RemoteEndpoint remoteEndpoint, boolean notify, Throwable cause) {
    if (!remoteEndpoints.remove(remoteEndpoint.address, remoteEndpoint)) {
      return;
    }
    remoteEndpoint.producer.close();
    TransportFrame.Builder builder = TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code).setReason("Remote endpoint down"));
    for (Long id : remoteEndpoint.streams) {
      EventBusGrpcStreamBase stream = streams.get(id);
      if (stream != null) {
        if (notify) {
          stream.registration.sendTransportFrame(builder, stream.format(), null);
        }
        stream.handleRemoteEndpointDown(cause);
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
      if (frame.getFrameCase() == TransportFrame.FrameCase.WINDOW_UPDATE) {
        stream.updateOutboundWindow(frame.getWindowUpdate().getDelta());
      } else {
        stream.handle(frame, message);
      }
    }
  }

  private void handlePing(Ping ping, Message<Object> message) {
    String remoteAddress = message.headers().get(EventBusHeaders.REMOTE_ENDPOINT_ADDRESS);
    if (remoteAddress == null) {
      return;
    }
    RemoteEndpoint remoteEndpoint = remoteEndpoints.get(remoteAddress);
    if (remoteEndpoint != null) {
      remoteEndpoint.lastSeen = System.currentTimeMillis();
    }
    if (ping.getAck()) {
      return;
    }

    String wireFormatName = message.headers().get(EventBusHeaders.WIRE_FORMAT);
    WireFormat wireFormat = JsonWireFormat.NAME.equals(wireFormatName) ? WireFormat.JSON : WireFormat.PROTOBUF;
    DeliveryOptions options = new DeliveryOptions()
      .addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name())
      .addHeader(EventBusHeaders.REMOTE_ENDPOINT_ADDRESS, address);
    Ping.Builder ack = Ping.newBuilder().setData(ping.getData()).setAck(true);
    eventBus.send(remoteAddress, EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setPing(ack), wireFormat), options);
  }

  Future<Void> closeStreams() {
    stopped = true;
    if (livenessTimerId >= 0) {
      vertx.cancelTimer(livenessTimerId);
      livenessTimerId = -1L;
    }
    for (RemoteEndpoint remoteEndpoint : remoteEndpoints.values()) {
      remoteEndpoint.producer.close();
    }
    remoteEndpoints.clear();
    List<EventBusGrpcStreamBase> active = new ArrayList<>(streams.values());
    streams.clear();
    TransportFrame.Builder builder = TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code).setReason("Closed"));
    List<Future<Void>> futures = new ArrayList<>();
    for (EventBusGrpcStreamBase stream : active) {
      stream.registration.sendTransportFrame(builder, stream.format(), null);
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

  public static final class RemoteEndpoint {

    private final String address;
    private final long timeout;
    public final MessageProducer<Object> producer;
    private final Set<Long> streams = ConcurrentHashMap.newKeySet();

    private volatile long lastSeen;

    private RemoteEndpoint(String address, long timeout, MessageProducer<Object> producer, long now) {
      this.address = address;
      this.timeout = timeout;
      this.producer = producer;
      this.lastSeen = now;
    }
  }

  final class StreamRegistration {

    private final long id;

    RemoteEndpoint remoteEndpoint;

    boolean closed;

    private StreamRegistration(long id) {
      this.id = id;
    }

    EventBusGrpcEndpoint localEndpoint() {
      return EventBusGrpcEndpoint.this;
    }

    RemoteEndpoint remoteEndpoint() {
      return remoteEndpoint;
    }

    long id() {
      return id;
    }

    void bind(EventBusGrpcStreamBase stream, String remoteAddress, long remoteTimeout) {
      streams.put(id, stream);
      RemoteEndpoint bound = remoteEndpoints.computeIfAbsent(remoteAddress, addr -> new RemoteEndpoint(addr, remoteTimeout, eventBus.sender(addr), System.currentTimeMillis()));
      bound.streams.add(id);
      remoteEndpoint = bound;
      scheduleLivenessCheck();
    }

    public Future<Void> sendTransportFrame(TransportFrame.Builder builder, WireFormat wireFormat, DeliveryOptions options) {
      RemoteEndpoint remote = remoteEndpoint;
      assert remote != null;
      builder.setStreamId(id);
      Object payload = EventBusGrpcCodec.encodeFrame(builder, wireFormat);
      if (options == null) {
        options = new DeliveryOptions();
      }
      options.addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name());
      MessageProducer<Object> producer = remote.producer;
      Future<Void> res = producer.write(payload, options);
      return res.andThen(ar -> {
        if (ar.failed()) {
          remoteEndpointDown(remoteEndpoint, false, ar.cause());
        }
      });
    }

    void unbind() {
      streams.remove(id);
      RemoteEndpoint bound = remoteEndpoint;
      if (!closed) {
        closed = true;
        if (bound != null) {
          bound.streams.remove(id);
          if (bound.streams.isEmpty() && remoteEndpoints.remove(bound.address, bound)) {
            bound.producer.close();
          }
        }
      }
    }
  }
}
