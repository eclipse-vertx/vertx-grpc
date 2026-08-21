package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.*;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

class EventBusGrpcServerCall extends EventBusGrpcStreamBase<EventBusGrpcServerEndpoint> {

  private final WireFormat wireFormat;
  private final String encoding;
  private final Inbound inbound;
  private final Outbound outbound;
  private boolean closed;

  public EventBusGrpcServerCall(
    EventBusGrpcServerEndpoint localEndpoint,
    long id,
    ContextInternal context,
    boolean localUnary,
    boolean remoteUnary,
    WireFormat wireFormat,
    String encoding,
    int initialInboundWindowSize,
    int initialOutboundWindowSize) {
    super(localEndpoint, id, context, localUnary, remoteUnary, initialInboundWindowSize, initialOutboundWindowSize);
    this.wireFormat = wireFormat;
    this.encoding = encoding;
    this.inbound = remoteUnary ? new UnaryInbound() : new StreamingInbound();
    this.outbound = localUnary && remoteUnary ? new UnaryOutbound() : new StreamingOutbound();
  }

  void handleConnect(Message<Object> message) {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);
    outbound.handleConnect(message);
    inbound.handleConnect(headers, message);
  }

  @Override
  WireFormat format() {
    return wireFormat;
  }

  @Override
  String encoding() {
    return encoding;
  }

  @Override
  void handleClosed() {
    closed = true;
  }

  private interface Inbound {
    void handleConnect(MultiMap headers, Message<Object> message);
  }

  private class UnaryInbound implements Inbound {
    @Override
    public void handleConnect(MultiMap headers, Message<Object> message) {
      Buffer payload = EventBusGrpcCodec.decodeBody(message.body());
      emitInbound(new DefaultGrpcHeadersFrame(wireFormat, "identity", headers));
      emitInbound(new DefaultGrpcMessageFrame(GrpcMessage.message("identity", wireFormat, payload)));
      emitInbound(DefaultGrpcHalfCloseFrame.INSTANCE);
      EventBusGrpcEndpoint.StreamRegistration sr = EventBusGrpcServerCall.this;
      sr.closeInbound();
    }
  }

  private class StreamingInbound implements Inbound {
    @Override
    public void handleConnect(MultiMap headers, Message<Object> message) {
      GrpcHeadersFrame frame = new DefaultGrpcHeadersFrame(wireFormat, "identity, ", headers);
      emitInbound(frame);
    }
  }

  private interface Outbound {
    void handleConnect(Message<Object> msg);
    Future<Void> write(GrpcFrame frame);
    Future<Void> end();
  }

  private class UnaryOutbound implements Outbound {

    private Message<Object> message;
    private MultiMap headers;
    private GrpcMessage encodedMessage;
    private boolean closed;

    @Override
    public void handleConnect(Message<Object> msg) {
      this.message = msg;
    }

    @Override
    public Future<Void> write(GrpcFrame frame) {
      if (closed) {
        return consumerContext.failedFuture("Outbound closed, no more frames accepted");
      }
      switch (frame.type()) {
        case HEADERS:
          headers = ((GrpcHeadersFrame) frame).headers();
          return consumerContext.succeededFuture();
        case MESSAGE:
          encodedMessage = ((GrpcMessageFrame) frame).message();
          return consumerContext.succeededFuture();
        case HALF_CLOSE:
          closed = true;
          GrpcTrailersFrame trailersFrame = (GrpcTrailersFrame) frame;
          handleTrailers(trailersFrame.status(), trailersFrame.statusMessage(), encodedMessage, headers, trailersFrame.trailers());
          return consumerContext.succeededFuture();
        default:
          return consumerContext.succeededFuture();
      }
    }

    @Override
    public Future<Void> end() {
      if (closed) {
        return consumerContext.succeededFuture();
      } else {
        return consumerContext.failedFuture("Frames should have been sent prior ending the stream");
      }
    }

    private void handleTrailers(GrpcStatus status, String statusMessage, GrpcMessage response, MultiMap headers, MultiMap trailers) {
      if (producerContext.inThread()) {
        if (status != GrpcStatus.OK) {
          String msg = statusMessage != null ? statusMessage : status.name();
          message.fail(status.code, msg);
        } else {
          DeliveryOptions options = new DeliveryOptions();
          MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
          if (headers != null) {
            EventBusHeaders.encodeMultiMap(HEADER_PREFIX, headers, multiMap);
          }
          if (trailers != null) {
            EventBusHeaders.encodeMultiMap(TRAILER_PREFIX, trailers, multiMap);
          }
          Buffer payload = response != null ? response.payload() : Buffer.buffer();
          options.setHeaders(multiMap);
          message.reply(EventBusGrpcCodec.encodeBody(payload, wireFormat), options);
        }
        EventBusGrpcEndpoint.StreamRegistration sr = EventBusGrpcServerCall.this;
        sr.closeOutbound();
      } else {
        producerContext.execute(() -> {
          handleTrailers(status, statusMessage, response, headers, trailers);
        });
      }
    }

  }

  private class StreamingOutbound implements Outbound {

    private Future<Void> lastWrite;

    @Override
    public void handleConnect(Message<Object> msg) {
      DeliveryOptions replyOptions = new DeliveryOptions()
        .addHeader(EventBusHeaders.ENDPOINT_ADDRESS, localEndpoint.address())
        .addHeader(EventBusHeaders.ENDPOINT_WIRE_FORMAT, format().name())
        .addHeader(EventBusHeaders.INITIAL_WINDOW, Integer.toString(localEndpoint.initialWindowSize));

      msg.reply(null, replyOptions);
    }

    @Override
    public Future<Void> write(GrpcFrame frame) {
      Future<Void> written;
      switch (frame.type()) {
        case HEADERS:
          MultiMap responseHeaders = ((GrpcHeadersFrame) frame).headers();
          written = writeResponseHeaders(responseHeaders);
          break;
        case HALF_CLOSE:
        case MESSAGE:
          written = enqueue(frame);
          break;
        default:
          return consumerContext.failedFuture("Invalid message: " + frame.type());
      }
      lastWrite = written;
      return written;
    }

    public Future<Void> end() {
      Future<Void> last = lastWrite;
      if (last == null) {
        return consumerContext.failedFuture(new IllegalStateException("Cannot end a stream that did not write any frame"));
      }
      return last;
    }

    private Future<Void> writeResponseHeaders(MultiMap headers) {
      DeliveryOptions options = new DeliveryOptions();
      if (headers != null && !headers.isEmpty()) {
        MultiMap delivery = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.encodeMultiMap(HEADER_PREFIX, headers, delivery);
        options.setHeaders(delivery);
      }
      options.addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name());
      return sendTransportFrame(TransportFrame.newBuilder().setHeaders(Headers.newBuilder()), options);
    }
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    if (closed) {
      return consumerContext.failedFuture("Stream closed");
    }
    return outbound.write(frame);
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    if (closed) {
      return consumerContext.failedFuture("Stream closed");
    }
    return outbound.end();
  }
}
