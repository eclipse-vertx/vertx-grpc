package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.Ack;
import io.vertx.grpc.eventbus.transport.Cancel;
import io.vertx.grpc.eventbus.transport.HalfClose;
import io.vertx.grpc.eventbus.transport.Trailers;
import io.vertx.grpc.eventbus.transport.TransportFrame;
import io.vertx.grpc.eventbus.transport.WindowUpdate;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

public class EventBusGrpcClientStreamingCall extends EventBusGrpcStreamBase {

  private final EventBus eventBus;
  private final ServiceName serviceName;
  private final String methodName;
  private final String clientAddress;
  private final Deque<GrpcMessage> pending = new ArrayDeque<>();

  private WireFormat wireFormat;
  private String encoding;
  private MultiMap requestHeaders;
  private Duration timeout;

  private State state = State.IDLE;
  private boolean ended;
  private String serverAddress;
  private MessageConsumer<Object> inboundConsumer;

  public EventBusGrpcClientStreamingCall(ContextInternal context, EventBus eventBus, ServiceName serviceName, String methodName) {
    super(context, DEFAULT_WINDOW);
    this.eventBus = eventBus;
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.clientAddress = "grpc.eb.client." + UUID.randomUUID();
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
        wireFormat = headersFrame.format();
        encoding = headersFrame.encoding();
        requestHeaders = headersFrame.headers();
        timeout = headersFrame.timeout();
        return context.succeededFuture();
      case MESSAGE:
        return onMessageWrite(((GrpcMessageFrame) frame).message());
      case CANCEL:
        return sendCancel();
      default:
        return context.succeededFuture();
    }
  }

  private Future<Void> onMessageWrite(GrpcMessage message) {
    switch (state) {
      case IDLE:
        return open(message);
      case OPENING:
        pending.add(message);
        return context.succeededFuture();
      case STREAMING:
        writeMessage(message);
        return context.succeededFuture();
      default:
        return context.failedFuture(new IllegalStateException("Stream closed"));
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    ended = true;
    switch (state) {
      case IDLE:
        return open(null);
      case STREAMING:
        sendHalfClose();
        return context.succeededFuture();
      default:
        return context.succeededFuture();
    }
  }

  private void sendHalfClose() {
    sendTerminal(() -> sendTransportFrame(TransportFrame.newBuilder().setHalfClose(HalfClose.newBuilder())));
  }

  private Future<Void> open(GrpcMessage firstMessage) {
    state = State.OPENING;

    WireFormat wf = Optional.ofNullable(this.wireFormat).orElse(WireFormat.PROTOBUF);
    String enc = Optional.ofNullable(this.encoding).orElse("identity");

    DeliveryOptions options = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, methodName)
      .addHeader(EventBusHeaders.WIRE_FORMAT, wf.name())
      .addHeader(EventBusHeaders.CLIENT_ADDRESS, clientAddress);

    if (timeout != null) {
      options.setSendTimeout(timeout.toMillis());
    }

    if (requestHeaders != null) {
      EventBusHeaders.encodeMultiMap(HEADER_PREFIX, requestHeaders, options.getHeaders());
    }

    Buffer payload = firstMessage != null ? firstMessage.payload() : Buffer.buffer();
    Object body = EventBusGrpcCodec.encodeBody(payload, wf);

    eventBus.request(serviceName.fullyQualifiedName(), body, options).onComplete(ar -> {
      if (ar.failed()) {
        handleFailure(ar.cause(), enc, wf);
      } else {
        handleAck(ar.result(), enc, wf);
      }
    });

    return context.succeededFuture();
  }

  private void handleFailure(Throwable cause, String encoding, WireFormat wireFormat) {
    GrpcStatus status = EventBusGrpcCodec.mapFailure(cause);
    emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, MultiMap.caseInsensitiveMultiMap()));
    emit(new DefaultGrpcTrailersFrame(status, cause.getMessage(), MultiMap.caseInsensitiveMultiMap()));
    state = State.CLOSED;
    emitEnd();
  }

  private void handleAck(Message<Object> reply, String encoding, WireFormat wireFormat) {
    Ack ack = EventBusGrpcCodec.decodeFrame(reply).getAck();
    this.serverAddress = ack.getServerAddress();
    this.encoding = encoding;
    this.wireFormat = wireFormat;

    state = State.STREAMING;
    grantSendWindow(ack.getInitialWindow());

    inboundConsumer = eventBus.consumer(clientAddress, this::handleInbound);
    inboundConsumer.completion().onComplete(ar -> {
      sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(window)));
      GrpcMessage message;
      while ((message = pending.poll()) != null) {
        writeMessage(message);
      }
      if (ended) {
        sendHalfClose();
      }
    });
  }

  private void handleInbound(Message<Object> message) {
    TransportFrame frame = EventBusGrpcCodec.decodeFrame(message);
    switch (frame.getFrameCase()) {
      case HEADERS:
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);
        emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, headers));
        break;
      case MESSAGE:
        onInboundMessage();
        emit(new DefaultGrpcMessageFrame(EventBusGrpcCodec.message(frame, encoding, wireFormat)));
        break;
      case WINDOW_UPDATE:
        grantSendWindow(frame.getWindowUpdate().getDelta());
        break;
      case TRAILERS:
        Trailers t = frame.getTrailers();
        MultiMap trailers = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(TRAILER_PREFIX, message.headers(), trailers);
        GrpcStatus status = Optional.ofNullable(GrpcStatus.valueOf(t.getStatus())).orElse(GrpcStatus.UNKNOWN);
        emit(new DefaultGrpcTrailersFrame(status, t.getStatusMessage().isEmpty() ? null : t.getStatusMessage(), trailers));
        close();
        emitEnd();
        break;
      case CANCEL:
        emitException(new CancellationException(frame.getCancel().getReason()));
        close();
        emitEnd();
        break;
      default:
        break;
    }
  }

  private Future<Void> sendCancel() {
    if (state == State.STREAMING) {
      sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code)));
    }
    close();
    return context.succeededFuture();
  }

  @Override
  protected void sendTransportFrame(TransportFrame.Builder builder) {
    if (serverAddress == null) {
      return;
    }
    eventBus.send(serverAddress, EventBusGrpcCodec.encodeFrame(builder));
  }

  private void close() {
    if (state == State.CLOSED) {
      return;
    }
    state = State.CLOSED;
    if (inboundConsumer != null) {
      inboundConsumer.unregister();
      inboundConsumer = null;
    }
  }

  private enum State {
    IDLE,
    OPENING,
    STREAMING,
    CLOSED
  }
}
