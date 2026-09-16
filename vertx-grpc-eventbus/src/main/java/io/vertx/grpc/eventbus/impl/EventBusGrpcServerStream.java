package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.*;

import java.util.Map;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;
import static io.vertx.grpc.eventbus.impl.Utils.toCanonicalName;

class EventBusGrpcServerStream extends EventBusGrpcStream<EventBusGrpcServerEndpoint> {

  private final WireFormat wireFormat;
  private final String encoding;
  private final boolean remoteUnary;
  private final Inbound inbound;
  private final Outbound outbound;
  private boolean closed;

  public EventBusGrpcServerStream(
    EventBusGrpcServerEndpoint localEndpoint,
    long id,
    ContextInternal context,
    boolean localUnary,
    boolean remoteUnary,
    WireFormat wireFormat,
    String encoding,
    int initialInboundWindowSize,
    int initialOutboundWindowSize) {
    super(localEndpoint, id, context, initialInboundWindowSize, initialOutboundWindowSize);
    this.wireFormat = wireFormat;
    this.encoding = encoding;
    this.remoteUnary = remoteUnary;

    this.inbound = remoteUnary ? new UnaryInbound() : new StreamingInbound();
    this.outbound = localUnary ? new UnaryOutbound() : new StreamingOutbound();
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
  void handleConsumerClosed() {
    closed = true;
    outbound.handleClose(closeCause);
  }

  private void sendAck() {
    TransportFrame.Builder frame = TransportFrame.newBuilder()
      .setAck(Ack.newBuilder()
        .setEndpointAddress(localEndpoint.address())
        .setEndpointWireFormat(toCanonicalName(localEndpoint.wireFormat))
        .setInitialWindow(localEndpoint.initialWindowSize));
    sendTransportFrame(frame, null);
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
      EventBusGrpcEndpoint.StreamRegistration sr = EventBusGrpcServerStream.this;
      sr.closeInbound();
    }
  }

  private class StreamingInbound implements Inbound {
    @Override
    public void handleConnect(MultiMap headers, Message<Object> message) {
      GrpcHeadersFrame frame = new DefaultGrpcHeadersFrame(wireFormat, "identity", headers);
      emitInbound(frame);
    }
  }

  private interface Outbound {
    void handleConnect(Message<Object> msg);
    Future<Void> write(GrpcFrame frame);
    Future<Void> end();
    void handleClose(Throwable cause);
  }

  private class UnaryOutbound implements Outbound {

    private Message<Object> message;
    private MultiMap headers;
    private GrpcMessage encodedMessage;
    private boolean closed;

    @Override
    public void handleConnect(Message<Object> msg) {
      this.message = msg;
      if (!remoteUnary) {
        sendAck();
      }
    }

    @Override
    public void handleClose(Throwable cause) {
      if (!closed && message != null) {
        closed = true;
        GrpcStatus status = cause != null ? EventBusGrpcCodec.mapFailure(cause) : GrpcStatus.CANCELLED;
        String msg = cause != null && cause.getMessage() != null ? cause.getMessage() : status.name();
        message.fail(status.code, msg);
      }
    }

    @Override
    public Future<Void> write(GrpcFrame frame) {
      if (closed) {
        return consumerContext.failedFuture("Outbound closed, no more frames accepted");
      }
      switch (frame.type()) {
        case HEADERS:
          headers = ((GrpcHeadersFrame) frame).metadata();
          return consumerContext.succeededFuture();
        case MESSAGE:
          encodedMessage = ((GrpcMessageFrame) frame).message();
          return consumerContext.succeededFuture();
        case HALF_CLOSE:
          closed = true;
          GrpcTrailersFrame trailersFrame = (GrpcTrailersFrame) frame;
          handleTrailers(trailersFrame.status(), trailersFrame.statusMessage(), encodedMessage, headers, trailersFrame.metadata());
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
          if (message != null) {
            message.fail(status.code, msg);
            message = null;
          }
        } else {
          DeliveryOptions options = new DeliveryOptions();
          options.setTracingPolicy(TracingPolicy.IGNORE);
          MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
          if (headers != null) {
            EventBusHeaders.encodeMultiMap(HEADER_PREFIX, headers, multiMap);
          }
          if (trailers != null) {
            EventBusHeaders.encodeMultiMap(TRAILER_PREFIX, trailers, multiMap);
          }
          Buffer payload = response != null ? response.payload() : Buffer.buffer();
          options.setHeaders(multiMap);
          if (message != null) {
            message.reply(EventBusGrpcCodec.encodeBody(payload, wireFormat), options);
            message = null;
          }
        }
        EventBusGrpcEndpoint.StreamRegistration sr = EventBusGrpcServerStream.this;
        sr.closeOutbound();
      } else {
        producerContext.execute(() -> {
          handleTrailers(status, statusMessage, response, headers, trailers);
        });
      }
    }

  }

  private class StreamingOutbound implements Outbound {

    private Message<Object> message;
    private Future<Void> lastWrite;

    @Override
    public void handleConnect(Message<Object> msg) {
      this.message = msg;
      sendAck();
    }

    @Override
    public void handleClose(Throwable cause) {
      if (message != null) {
        GrpcStatus status = cause != null ? EventBusGrpcCodec.mapFailure(cause) : GrpcStatus.CANCELLED;
        String msg = cause != null && cause.getMessage() != null ? cause.getMessage() : status.name();
        message.fail(status.code, msg);
        message = null;
      }
    }

    @Override
    public Future<Void> write(GrpcFrame frame) {
      Future<Void> written;
      switch (frame.type()) {
        case HEADERS:
          MultiMap responseHeaders = ((GrpcHeadersFrame) frame).metadata();
          written = writeResponseHeaders(responseHeaders);
          break;
        case HALF_CLOSE:
          Future<Void> pending = lastWrite;
          written = pending == null ? handleTrailers((GrpcTrailersFrame) frame) : pending.compose(v -> handleTrailers((GrpcTrailersFrame) frame));
          break;
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

    private Future<Void> handleTrailers(GrpcTrailersFrame frame) {
      if (producerContext.inThread()) {
        GrpcStatus status = frame.status();
        if (status != GrpcStatus.OK) {
          String msg = frame.statusMessage() != null ? frame.statusMessage() : status.name();
          if (message != null) {
            message.fail(status.code, msg);
            message = null;
          }
        } else {
          DeliveryOptions options = new DeliveryOptions();
          options.setTracingPolicy(TracingPolicy.IGNORE);
          MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
          if (frame.metadata() != null) {
            EventBusHeaders.encodeMultiMap(TRAILER_PREFIX, frame.metadata(), multiMap);
          }
          options.setHeaders(multiMap);
          if (message != null) {
            message.reply(null, options);
            message = null;
          }
        }
        EventBusGrpcEndpoint.StreamRegistration sr = EventBusGrpcServerStream.this;
        sr.closeOutbound();
        return producerContext.succeededFuture();
      } else {
        Promise<Void> p = consumerContext.promise();
        producerContext.execute(() -> {
          handleTrailers(frame).onComplete(p);
        });
        return p.future();
      }
    }

    private Future<Void> writeResponseHeaders(MultiMap headers) {
      Headers.Builder headersBuilder = Headers.newBuilder();
      if (headers != null && !headers.isEmpty()) {
        for (Map.Entry<String, String> entry : headers) {
          headersBuilder.putMetadata(entry.getKey(), entry.getValue());
        }
      }
      DeliveryOptions options = new DeliveryOptions();
      options.addHeader(EventBusHeaders.STREAM_WIRE_FORMAT, toCanonicalName(wireFormat));
      return sendTransportFrame(TransportFrame.newBuilder().setHeaders(headersBuilder), options);
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
