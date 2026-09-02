package io.vertx.grpc.eventbus.impl;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.eventbus.EventBusInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.eventbus.transport.v1alpha.Cancel;
import io.vertx.grpc.eventbus.transport.v1alpha.Ping;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

abstract class EventBusGrpcEndpoint {

  private final ContextInternal producerContext;
  private final VertxInternal vertx;
  private final EventBusInternal eventBus;
  private final String address;
  private final int id;
  private final AtomicInteger sequence = new AtomicInteger();
  private final long pingInterval;
  private final LongObjectMap<EventBusGrpcStream<?>> streams = new LongObjectHashMap<>();
  private final Map<String, RemoteEndpoint> remoteEndpoints = new HashMap<>();
  private final AtomicLong pingData = new AtomicLong();
  final WireFormat wireFormat;
  protected final int initialWindowSize;
  private MessageConsumer<TransportFrame> consumer;
  private final long cleanerPeriod;
  private long livenessTimerId = -1L;

  EventBusGrpcEndpoint(ContextInternal producerContext,
                       WireFormat wireFormat,
                       String prefix,
                       long cleanerPeriod,
                       long pingInterval,
                       int initialWindowSize) {

    UUID uuid = UUID.randomUUID();

    EventBusInternal eventBus = producerContext.owner().eventBus();
    try {
      eventBus.registerCodec(EventBusGrpcProtobufMessageCodec.INSTANCE);
      eventBus.registerCodec(EventBusGrpcJsonMessageCodec.INSTANCE);
    } catch (IllegalStateException e) {
      // Already registered ...
    }

    this.wireFormat = wireFormat;
    this.vertx = producerContext.owner();
    this.producerContext = producerContext;
    this.eventBus = vertx.eventBus();
    this.id = uuid.hashCode() & 0x7FFFFFFF;
    this.address = prefix + uuid;
    this.pingInterval = pingInterval;
    this.initialWindowSize = initialWindowSize;
    this.cleanerPeriod = cleanerPeriod;
  }

  public ContextInternal producerContext() {
    return producerContext;
  }

  int id() {
    return id;
  }

  public String address() {
    return address;
  }

  long nextStreamId() {
    return ((long)id()) << 32 | sequence.getAndIncrement();
  }

  long pingInterval() {
    return pingInterval;
  }

  void bind(Promise<Void> promise) {
    consumer = consumer(address, this::dispatch);
    consumer
      .completion()
      .andThen(ar -> {
        if (ar.succeeded()) {
          livenessTimerId = scheduleLivenessCheck();
        }
      }).onComplete(promise);
  }

  private long scheduleLivenessCheck() {
    return producerContext.setTimer(cleanerPeriod, id -> {
      livenessTimerId = -1L;
      long now = System.currentTimeMillis();
      pingRemoteEndpoints(now);
      reapSilentRemoteEndpoints(now);
      livenessTimerId = scheduleLivenessCheck();
    });
  }

  private void cancelLivenessCheck() {
    long timer = livenessTimerId;
    if (timer != -1) {
      vertx.cancelTimer(timer);
    }
  }

  Future<Message<Object>> request(String address, Object body, DeliveryOptions options) {
    return eventBus.request(producerContext, address, body, options);
  }

  <T> MessageConsumer<T> consumer(String address, Handler<Message<T>> handler) {
    MessageConsumer<T> consumer = eventBus.consumer(producerContext, new MessageConsumerOptions().setAddress(address));
    consumer.handler(handler);
    return consumer;
  }

  private void pingRemoteEndpoints(long now) {
    if (pingInterval > 0) {
      // Only clients
      long data = pingData.incrementAndGet();
      List<RemoteEndpoint> toPing = new ArrayList<>();
      for (RemoteEndpoint remoteEndpoint : remoteEndpoints.values()) {
        if (now - remoteEndpoint.lastPingTimestamp > pingInterval) {
          toPing.add(remoteEndpoint);
        }
      }
      for (RemoteEndpoint remoteEndpoint : toPing) {
        TransportFrame frame = TransportFrame.newBuilder().setPing(Ping.newBuilder().setData(data)).build();
        DeliveryOptions options = new DeliveryOptions()
          .addHeader(EventBusHeaders.ENDPOINT_WIRE_FORMAT, remoteEndpoint.format.name())
          .addHeader(EventBusHeaders.ENDPOINT_ADDRESS, address);
        switch (remoteEndpoint.format.name()) {
          case "json":
            options.setCodecName(EventBusGrpcJsonMessageCodec.CODEC_NAME);
            break;
          case "proto":
            options.setCodecName(EventBusGrpcProtobufMessageCodec.CODEC_NAME);
            break;
          default:
            throw new UnsupportedOperationException();
        }
        remoteEndpoint.lastPingTimestamp = now;
        remoteEndpoint.producer
          .write(frame, options)
          .onFailure(cause -> remoteEndpointDown(remoteEndpoint, false));
      }
    }
  }

  private void reapSilentRemoteEndpoints(long now) {
    for (RemoteEndpoint remoteEndpoint : new ArrayList<>(remoteEndpoints.values())) {
      if (remoteEndpoint.timeout > 0 && now - remoteEndpoint.lastSeenTimestamp > remoteEndpoint.timeout) {
        GrpcErrorException err = new GrpcErrorException(GrpcError.UNAVAILABLE);
        err.initCause(new TimeoutException("No ping from remote endpoint " + remoteEndpoint.address + " within " + remoteEndpoint.timeout + " ms"));
        remoteEndpointDown(remoteEndpoint, true);
      }
    }
  }

  private void remoteEndpointDown(RemoteEndpoint remoteEndpoint, boolean notify) {
    if (remoteEndpoints.get(remoteEndpoint.address) == remoteEndpoint) {
      ArrayList<StreamRegistration> copy = new ArrayList<>(remoteEndpoint.streams.values());
      for (StreamRegistration registration : copy) {
        registration.close(new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED), notify);
      }
      // Check endpoint is down
      assert !remoteEndpoints.containsKey(remoteEndpoint.address);
    }
  }

  private void dispatch(Message<TransportFrame> message) {
    TransportFrame frame = message.body();
    if (frame.getFrameCase() == TransportFrame.FrameCase.PING) {
      handlePing(frame.getPing(), message);
      return;
    }
    EventBusGrpcStream<?> stream = streams.get(frame.getStreamId());
    if (stream != null) {
      if (frame.getFrameCase() == TransportFrame.FrameCase.CANCEL) {
        stream.close(new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED), false);
      } else {
        stream.handle(frame, message);
      }
    }
  }

  private void handlePing(Ping ping, Message<TransportFrame> message) {
    String remoteAddress = message.headers().get(EventBusHeaders.ENDPOINT_ADDRESS);
    RemoteEndpoint remoteEndpoint = remoteEndpoints.get(remoteAddress);
    if (remoteEndpoint != null) {
      remoteEndpoint.lastSeenTimestamp = System.currentTimeMillis();
      if (!ping.getAck()) {
        DeliveryOptions options = new DeliveryOptions()
          .addHeader(EventBusHeaders.ENDPOINT_WIRE_FORMAT, wireFormat.name())
          .addHeader(EventBusHeaders.ENDPOINT_ADDRESS, address);
        Ping.Builder ack = Ping.newBuilder().setData(ping.getData()).setAck(true);
        remoteEndpoint.sendTransportFrame(TransportFrame.newBuilder().setPing(ack).build(), options);
      }
    }
  }

  protected abstract Future<Void> handleClose();

  public Future<Void> close() {
    PromiseInternal<Void> completion = vertx.promise();
    if (producerContext.inThread()) {
      closeImpl(completion);
    } else {
      producerContext.execute(v -> closeImpl(completion));
    }
    return completion.future();
  }

  private void closeImpl(Promise<Void> completion) {
    cancelLivenessCheck();
    handleClose()
      .eventually(this::closeStreams)
      .eventually(() -> consumer.unregister())
      .onComplete(completion);
  }

  private RemoteEndpoint remoteEndpoint(String remoteAddress, long remoteTimeout,  WireFormat wireFormat) {
    return remoteEndpoints.computeIfAbsent(remoteAddress,
      addr -> new RemoteEndpoint(addr, remoteTimeout, eventBus.sender(addr),
        System.currentTimeMillis(), wireFormat));
  }

  private Future<?> closeStreams() {
    List<Future<?>> results = new ArrayList<>();
    for (EventBusGrpcStream<?> stream : new ArrayList<>(streams.values())) {
      Future<Void> result = stream.close(GrpcError.CANCELLED, true);
      if (result != null) {
        results.add(result);
      }
    }
    assert remoteEndpoints.isEmpty();
    return Future.join(results);
  }

  public final class RemoteEndpoint {

    private final String address;
    private final long timeout;
    public final MessageProducer<Object> producer;
    private final LongObjectMap<StreamRegistration> streams = new LongObjectHashMap<>();
    private final WireFormat format;
    private long lastPingTimestamp;
    private long lastSeenTimestamp;

    private RemoteEndpoint(String address, long timeout, MessageProducer<Object> producer, long now, WireFormat format) {
      this.address = address;
      this.timeout = timeout;
      this.producer = producer;
      this.lastSeenTimestamp = now;
      this.lastPingTimestamp = 0L;
      this.format = format;
    }

    Future<Void> dispose() {
      assert streams.isEmpty();
      remoteEndpoints.remove(address, this);
      return producer.close();
    }

    Future<Void> sendTransportFrame(TransportFrame frame, DeliveryOptions options) {
      switch (format.name()) {
        case "json":
          options.setCodecName(EventBusGrpcJsonMessageCodec.CODEC_NAME);
          break;
        case "proto":
          options.setCodecName(EventBusGrpcProtobufMessageCodec.CODEC_NAME);
          break;
        default:
          throw new UnsupportedOperationException();
      }
      return producer.write(frame, options);
    }
  }

  static abstract class StreamRegistration {

    private final EventBusGrpcEndpoint localEndpoint;
    private final long id;
    private RemoteEndpoint remoteEndpoint;
    private boolean outboundClosed;
    private boolean inboundClosed;
    private Throwable closeReason;

    StreamRegistration(EventBusGrpcEndpoint localEndpoint, long id) {
      assert id >= 0;
      this.localEndpoint = localEndpoint;
      this.id = id;
    }

    abstract WireFormat format();

    abstract void handleProducerClosed(Throwable cause);

    final long id() {
      return id;
    }

    final void registerStream() {
      assert localEndpoint.producerContext.inThread();
      assert id > 0;
      localEndpoint.streams.put(id, (EventBusGrpcStream<?>) this);
    }

    final void registerRemoteEndpoint(String remoteAddress, long remoteTimeout, WireFormat wireFormat) {
      assert localEndpoint.producerContext.inThread();
      RemoteEndpoint bound = localEndpoint.remoteEndpoint(remoteAddress, remoteTimeout, wireFormat);
      bound.streams.put(id, this);
      remoteEndpoint = bound;
    }

    Future<Void> sendTransportFrame(TransportFrame.Builder builder, DeliveryOptions options) {
      assert localEndpoint.producerContext.inThread();
      RemoteEndpoint remote = remoteEndpoint;
      if (remote == null) {
        return localEndpoint.producerContext.failedFuture("Endpoint absent");
      } else {
        builder.setStreamId(id);
        TransportFrame frame = builder.build();
        if (frame.getFrameCase() == TransportFrame.FrameCase.HALF_CLOSE || frame.getFrameCase() == TransportFrame.FrameCase.TRAILERS) {
          closeOutbound();
        } else if (frame.getFrameCase() == TransportFrame.FrameCase.CANCEL) {
          // Should use a root cause for this to get unavailable instead ?
          close(GrpcError.CANCELLED, false);
        }
        if (options == null) {
          options = new DeliveryOptions();
        }
        options.addHeader(EventBusHeaders.STREAM_WIRE_FORMAT, format().name());
        switch (remote.format.name()) {
          case "json":
            options.setCodecName(EventBusGrpcJsonMessageCodec.CODEC_NAME);
            break;
          case "proto":
            options.setCodecName(EventBusGrpcProtobufMessageCodec.CODEC_NAME);
            break;
          default:
            throw new UnsupportedOperationException();
        }

        Future<Void> res = remote.sendTransportFrame(frame, options);
        return res.andThen(ar -> {
          if (ar.failed()) {
            localEndpoint.remoteEndpointDown(remote, false);
          }
        });
      }
    }

    final void closeInbound() {
      assert localEndpoint.producerContext.inThread();
      inboundClosed = true;
      if (outboundClosed) {
        remove();
        handleProducerClosed(null);
      }
    }

    final void closeOutbound() {
      assert localEndpoint.producerContext.inThread();
      outboundClosed = true;
      if (inboundClosed) {
        remove();
        handleProducerClosed(null);
      }
    }

    Future<Void> close(GrpcError cause, boolean notify) {
      return close(new GrpcErrorException(cause), notify);
    }

    Future<Void> close(Throwable cause, boolean notify) {
      assert localEndpoint.producerContext.inThread();
      if (!inboundClosed || !outboundClosed) {
        closeReason = cause;
        inboundClosed = true;
        outboundClosed = true;
        if (cause != null && remoteEndpoint != null && notify) {
          // Send a transport frame that bypasses the accounting
          TransportFrame frame = TransportFrame.newBuilder()
            .setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code).setReason("Closed"))
            .setStreamId(id)
            .build();
          remoteEndpoint.sendTransportFrame(frame, new DeliveryOptions());
        }
        Future<Void> res = remove();
        handleProducerClosed(cause);
        return res;
      } else {
        return null;
      }
    }

    private Future<Void> remove() {
      localEndpoint.streams.remove(id);
      RemoteEndpoint rm = remoteEndpoint;
      if (rm != null) {
        remoteEndpoint = null;
        rm.streams.remove(id);
        if (rm.streams.isEmpty()) {
          return rm.dispose();
        }
      }
      return null;
    }
  }
}
