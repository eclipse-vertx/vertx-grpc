package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcErrorException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.Cancel;
import io.vertx.grpc.eventbus.transport.v1alpha.Headers;
import io.vertx.grpc.eventbus.transport.v1alpha.Trailers;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

class EventBusGrpcServerStreamingCall extends EventBusGrpcStreamBase {

  private final EventBus eventBus;
  private final EventBusStreamEndpoint.StreamRegistration registration;
  private final String clientAddress;
  private final long clientStreamId;
  private final WireFormat wireFormat;
  private final String encoding;
  private final MessageProducer<Object> producer;

  private boolean clientListening;
  private boolean headersPending;
  private MultiMap pendingHeaders;
  private boolean closed;

  public EventBusGrpcServerStreamingCall(
    ContextInternal context,
    EventBus eventBus,
    EventBusStreamEndpoint.StreamRegistration registration,
    String clientAddress,
    long clientStreamId,
    WireFormat wireFormat,
    String encoding,
    int window,
    long producerHeartbeat,
    long consumerIdleTimeout
  ) {
    super(context, window);
    this.eventBus = eventBus;
    this.registration = registration;
    this.clientAddress = clientAddress;
    this.clientStreamId = clientStreamId;
    this.wireFormat = wireFormat;
    this.encoding = encoding;
    this.producer = eventBus.sender(clientAddress, new DeliveryOptions().addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name()));
    configureLiveness(producerHeartbeat, consumerIdleTimeout);
  }

  void start() {
    startHeartbeat();
    startIdleTimeout();
  }

  @Override
  public void handle(TransportFrame frame, Message<Object> message) {
    resetIdleTimeout();
    if (!clientListening) {
      clientListening = true;
      if (headersPending) {
        headersPending = false;
        sendResponseHeaders(pendingHeaders);
      }
    }

    switch (frame.getFrameCase()) {
      case MESSAGE:
        emit(new DefaultGrpcMessageFrame(EventBusGrpcCodec.message(frame, encoding, wireFormat)));
        break;
      case HALF_CLOSE:
        cancelIdleTimeout();
        emitEnd();
        break;
      case WINDOW_UPDATE:
        grantSendWindow(frame.getWindowUpdate().getDelta());
        break;
      case CANCEL:
        if (closed) {
          break;
        }
        terminate();
        emitException(new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED));
        break;
      case HEARTBEAT:
        break;
      default:
        break;
    }
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        MultiMap responseHeaders = ((GrpcHeadersFrame) frame).headers();
        if (clientListening) {
          sendResponseHeaders(responseHeaders);
        } else {
          pendingHeaders = responseHeaders;
          headersPending = true;
        }
        return context.succeededFuture();
      case MESSAGE:
        return writeMessage(((GrpcMessageFrame) frame).message());
      case TRAILERS:
        enqueue(new TrailersWrite((GrpcTrailersFrame) frame));
        return context.succeededFuture();
      default:
        return context.succeededFuture();
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    return context.succeededFuture();
  }

  private void sendResponseHeaders(MultiMap headers) {
    DeliveryOptions options = new DeliveryOptions();
    if (headers != null && !headers.isEmpty()) {
      MultiMap delivery = MultiMap.caseInsensitiveMultiMap();
      EventBusHeaders.encodeMultiMap(HEADER_PREFIX, headers, delivery);
      options.setHeaders(delivery);
    }
    options.addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name());
    eventBus.send(clientAddress, EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setStreamId(clientStreamId).setHeaders(Headers.newBuilder()), wireFormat), options);
  }

  private void sendTrailers(GrpcTrailersFrame frame) {
    Trailers.Builder trailers = Trailers.newBuilder().setStatus(frame.status().code);
    if (frame.statusMessage() != null) {
      trailers.setStatusMessage(frame.statusMessage());
    }
    DeliveryOptions options = new DeliveryOptions();
    MultiMap headers = frame.trailers();
    if (headers != null && !headers.isEmpty()) {
      MultiMap delivery = MultiMap.caseInsensitiveMultiMap();
      EventBusHeaders.encodeMultiMap(TRAILER_PREFIX, headers, delivery);
      options.setHeaders(delivery);
    }
    options.addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name());
    eventBus.send(clientAddress, EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setStreamId(clientStreamId).setTrailers(trailers), wireFormat), options);
  }

  @Override
  public void close(Completable<Void> completion) {
    if (!closed) {
      sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.UNAVAILABLE.code).setReason("Server closed")));
      terminate();
      emitException(new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED));
    }
    completion.succeed();
  }

  @Override
  protected Future<Void> sendTransportFrame(TransportFrame.Builder builder) {
    builder.setStreamId(clientStreamId);
    Future<Void> sent = producer.write(EventBusGrpcCodec.encodeFrame(builder, wireFormat));
    sent.onFailure(this::handleTransportFailure);
    return sent;
  }

  private void handleTransportFailure(Throwable cause) {
    if (closed) {
      return;
    }
    terminate();
    failPendingWrites(cause);
    emitException(new GrpcErrorException(GrpcError.UNAVAILABLE, GrpcStatus.UNAVAILABLE));
  }

  @Override
  protected void handleIdleTimeout() {
    sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code).setReason("Idle timeout")));
    handleTransportFailure(new java.util.concurrent.TimeoutException("No frames received from the client within the idle timeout"));
  }

  private void terminate() {
    if (closed) {
      return;
    }
    closed = true;
    stopLiveness();
    registration.unbind();
  }

  private final class TrailersWrite implements MessageWrite {

    private final GrpcTrailersFrame frame;

    TrailersWrite(GrpcTrailersFrame frame) {
      this.frame = frame;
    }

    @Override
    public void write() {
      sendTrailers(frame);
      terminate();
    }
  }
}
