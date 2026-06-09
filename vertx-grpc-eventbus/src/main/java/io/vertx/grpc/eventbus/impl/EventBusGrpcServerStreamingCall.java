package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcErrorException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.Headers;
import io.vertx.grpc.eventbus.transport.Trailers;
import io.vertx.grpc.eventbus.transport.TransportFrame;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

public class EventBusGrpcServerStreamingCall extends EventBusGrpcStreamBase {

  private final EventBus eventBus;
  private final String clientAddress;
  private final WireFormat wireFormat;
  private final String encoding;

  private MessageConsumer<Object> inboundConsumer;
  private boolean clientListening;
  private boolean headersPending;
  private MultiMap pendingHeaders;
  private boolean closed;

  public EventBusGrpcServerStreamingCall(
    ContextInternal context,
    EventBus eventBus,
    MessageConsumer<Object> inboundConsumer,
    String clientAddress,
    WireFormat wireFormat,
    String encoding,
    int window
  ) {
    super(context, window);
    this.eventBus = eventBus;
    this.clientAddress = clientAddress;
    this.wireFormat = wireFormat;
    this.encoding = encoding;
    this.inboundConsumer = inboundConsumer;
  }

  void handleInbound(Message<Object> message) {
    if (!clientListening) {
      clientListening = true;
      if (headersPending) {
        headersPending = false;
        sendResponseHeaders(pendingHeaders);
      }
    }

    TransportFrame frame = EventBusGrpcCodec.decodeFrame(message);
    switch (frame.getFrameCase()) {
      case MESSAGE:
        onInboundMessage();
        emit(new DefaultGrpcMessageFrame(EventBusGrpcCodec.message(frame, encoding, wireFormat)));
        break;
      case HALF_CLOSE:
        emitEnd();
        break;
      case WINDOW_UPDATE:
        grantSendWindow(frame.getWindowUpdate().getDelta());
        break;
      case CANCEL:
        if (closed) {
          break;
        }
        close();
        emitException(new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED));
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
        writeMessage(((GrpcMessageFrame) frame).message());
        return context.succeededFuture();
      case TRAILERS:
        GrpcTrailersFrame trailersFrame = (GrpcTrailersFrame) frame;
        sendTerminal(() -> sendTrailers(trailersFrame));
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
    eventBus.send(clientAddress, EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setHeaders(Headers.newBuilder())), options);
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
    eventBus.send(clientAddress, EventBusGrpcCodec.encodeFrame(TransportFrame.newBuilder().setTrailers(trailers)), options);
  }

  @Override
  protected void onTerminalSent() {
    close();
  }

  @Override
  protected void sendTransportFrame(TransportFrame.Builder builder) {
    eventBus.send(clientAddress, EventBusGrpcCodec.encodeFrame(builder));
  }

  private void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (inboundConsumer != null) {
      inboundConsumer.unregister();
      inboundConsumer = null;
    }
  }
}
