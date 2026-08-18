package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.eventbus.transport.v1alpha.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

class EventBusGrpcClientCall extends EventBusGrpcStreamBase {

  private final EventBusGrpcEndpoint endpoint;
  private final ServiceName serviceName;
  private final String methodName;

  private WireFormat wireFormat;
  private String encoding;
  private MultiMap requestHeaders;
  private Duration timeout;

  private boolean ended;
  private State state;

  private final Outbound outbound;
  private final Inbound inbound;

  public EventBusGrpcClientCall(ContextInternal context, boolean localUnary, boolean remoteUnary,
                                EventBusGrpcEndpoint.StreamRegistration registration, EventBusGrpcEndpoint endpoint,
                                ServiceName serviceName, String methodName, int initialInboundWindowSize, int initialOutboundWindowSize) {
    super(context, localUnary, remoteUnary, registration, initialInboundWindowSize, initialOutboundWindowSize);
    this.endpoint = endpoint;
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.state = State.IDLE;
    this.outbound = localUnary ? new UnaryOutbound() : new StreamingOutbound();
    this.encoding = "identity";
    this.wireFormat = WireFormat.PROTOBUF;
    this.inbound = remoteUnary && localUnary ? new UnaryInbound() : new StreamingInbound();
  }

  private interface Outbound {
    Future<Void> write(GrpcFrame frame);
    Future<Void> end();
  }

  private class UnaryOutbound implements Outbound {

    private GrpcMessage message;

    @Override
    public Future<Void> write(GrpcFrame frame) {
      switch (frame.type()) {
        case HEADERS:
          GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
          if (headersFrame.format() != null) {
            wireFormat = headersFrame.format();
          }
          if (headersFrame.encoding() != null) {
            encoding = headersFrame.encoding();
          }
          requestHeaders = headersFrame.headers();
          timeout = headersFrame.timeout();
          return consumerContext.succeededFuture();
        case MESSAGE:
          message = ((GrpcMessageFrame) frame).message();
          return consumerContext.succeededFuture();
        default:
          return consumerContext.failedFuture("Frame not handled");
      }
    }

    @Override
    public Future<Void> end() {
      GrpcMessage msg = message;
      if (msg == null) {
        return consumerContext.failedFuture("No message to send");
      }
      ended = true;
      message = null;
      return send(msg);
    }

    private Future<Void> send(GrpcMessage message) {

      DeliveryOptions options = new DeliveryOptions()
        .addHeader(EventBusHeaders.ACTION, methodName)
        .addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name())
        .addHeader(EventBusHeaders.CLIENT_ADDRESS, endpoint.address())
        .addHeader(EventBusHeaders.STREAM_ID, Long.toString(registration.id()));

      if (!remoteUnary) {
        options.addHeader(EventBusHeaders.INITIAL_WINDOW, "" + endpoint.initialWindowSize);
      }

      if (timeout != null) {
        options.setSendTimeout(timeout.toMillis());
      }

      if (requestHeaders != null) {
        EventBusHeaders.encodeMultiMap(HEADER_PREFIX, requestHeaders, options.getHeaders());
      }

      Buffer payload = message != null ? message.payload() : Buffer.buffer();
      Object body = EventBusGrpcCodec.encodeBody(payload, wireFormat);

      Promise<Void> promise = consumerContext.promise();

      endpoint.request(consumerContext, serviceName.fullyQualifiedName(), body, options).onComplete(ar -> {
        if (ar.succeeded()) {
          Throwable malformed = inbound._handleReply(ar.result(), encoding, wireFormat);
          if (malformed == null) {
            // Something specific to do ?
          } else {
            handleFailure(malformed, encoding, wireFormat);
          }
          promise.succeed();
        } else {
          InvalidStatusException err = handleFailure(ar.cause(), EventBusGrpcClientCall.this.encoding, EventBusGrpcClientCall.this.wireFormat);
          promise.fail(err);
        }
      });

      return promise.future();
    }
  }

  private class StreamingOutbound implements Outbound {
    @Override
    public Future<Void> write(GrpcFrame frame) {
      switch (frame.type()) {
        case HEADERS:
          GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
          if (headersFrame.format() != null) {
            wireFormat = headersFrame.format();
          }
          if (headersFrame.encoding() != null) {
            encoding = headersFrame.encoding();
          }
          requestHeaders = headersFrame.headers();
          timeout = headersFrame.timeout();
          return open();
        case MESSAGE:
          return onMessageWrite(((GrpcMessageFrame) frame).message());
        default:
          return consumerContext.failedFuture("Invalid message: " + frame.type());
      }
    }

    @Override
    public Future<Void> end() {
      if (state == State.STREAMING) {
        sendHalfClose();
      }
      return consumerContext.succeededFuture();
    }

    private Future<Void> open() {
      state = State.OPENING;

      DeliveryOptions options = new DeliveryOptions()
        .addHeader(EventBusHeaders.ACTION, methodName)
        .addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name())
        .addHeader(EventBusHeaders.CLIENT_ADDRESS, endpoint.address())
        .addHeader(EventBusHeaders.STREAM_ID, Long.toString(registration.id()));

      if (!remoteUnary) {
        options.addHeader(EventBusHeaders.INITIAL_WINDOW, "" + endpoint.initialWindowSize);
      }

      if (endpoint.pingTimeout() > 0) {
        options.addHeader(EventBusHeaders.PING_TIMEOUT, Long.toString(endpoint.pingTimeout()));
      }

      if (timeout != null) {
        options.setSendTimeout(timeout.toMillis());
      }

      if (requestHeaders != null) {
        EventBusHeaders.encodeMultiMap(HEADER_PREFIX, requestHeaders, options.getHeaders());
      }

      Promise<Void> promise = consumerContext.promise();
      endpoint.request(consumerContext, serviceName.fullyQualifiedName(), Buffer.buffer(), options).onComplete(ar -> {
        if (ar.failed()) {
          handleFailure(ar.cause(), encoding, wireFormat);
          promise.fail(ar.cause());
          return;
        }

        Throwable malformed = inbound._handleReply(ar.result(), encoding, wireFormat);
        if (malformed == null) {

          if (ended) {
            sendHalfClose();
          }

        } else {
          handleFailure(malformed, encoding, wireFormat);
        }
        promise.complete();
      });
      return promise.future();
    }

    private void sendHalfClose() {
      enqueue(new HalfCloseWrite());
    }

    private final class HalfCloseWrite implements MessageWrite {
      @Override
      public boolean write() {
        return sendTransportFrame(TransportFrame.newBuilder().setHalfClose(HalfClose.newBuilder())) != null;
      }
    }
  }

  private abstract class Inbound {
    Throwable _handleReply(Message<Object> reply, String encoding, WireFormat wireFormat) {

      String serverAddress;
      if (!localUnary || !remoteUnary) {
        MultiMap replyHeaders = reply.headers();
        serverAddress = replyHeaders.get(EventBusHeaders.SERVER_ADDRESS);
        if (serverAddress == null) {
          return new IllegalStateException("Malformed handshake reply: missing grpc-server-address header");
        }
      } else {
        serverAddress = null;
      }

      if (serverAddress != null) {
        // This could be racy since we are on the request/reply context ...
        registration.bind(EventBusGrpcClientCall.this, serverAddress, endpoint.pingTimeout());
      }

      Throwable err = handleReply(reply, encoding, wireFormat);

      if (err != null && serverAddress != null) {
        registration.unbind();
      }

      return err;
    }
    Throwable handleReply(Message<Object> reply, String encoding, WireFormat wireFormat) {

      int initialOutboundWindowSize;
      if (remoteUnary) {
        initialOutboundWindowSize = EventBusGrpcServerOptions.DEFAULT_INITIAL_WINDOW_SIZE;
      } else {
        String initialWindowHeader = reply.headers().get(EventBusHeaders.INITIAL_WINDOW);
        if (initialWindowHeader == null) {
          return new IllegalStateException("Malformed handshake reply: missing grpc-initial-window header");
        }
        try {
          initialOutboundWindowSize = Integer.parseInt(initialWindowHeader);
        } catch (NumberFormatException e) {
          return new IllegalStateException("Malformed handshake reply: non-numeric grpc-initial-window header");
        }
        if (initialOutboundWindowSize <= 0) {
          return new IllegalStateException("Malformed handshake reply: invalid grpc-initial-window header");
        }
      }
      updateOutboundWindow(initialOutboundWindowSize - EventBusGrpcServerOptions.DEFAULT_INITIAL_WINDOW_SIZE);
      return null;
    }
  }

  class StreamingInbound extends Inbound {

    @Override
    Throwable handleReply(Message<Object> reply, String encoding, WireFormat wireFormat) {

      Throwable err = super.handleReply(reply, encoding, wireFormat);
      if (err == null) {
        EventBusGrpcClientCall.this.encoding = encoding;
        EventBusGrpcClientCall.this.wireFormat = wireFormat;
        EventBusGrpcClientCall.this.state = State.STREAMING;
      }

      return err;
    }
  }

  class UnaryInbound extends Inbound {

    @Override
    Throwable handleReply(Message<Object> reply, String encoding, WireFormat wireFormat) {

      Throwable err = super.handleReply(reply, encoding, wireFormat);
      if (err == null) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        MultiMap trailers = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(HEADER_PREFIX, reply.headers(), headers);
        EventBusHeaders.decodeMultimap(TRAILER_PREFIX, reply.headers(), trailers);
        Buffer payload = EventBusGrpcCodec.decodeBody(reply.body());
        dispatchFrameInbound(new DefaultGrpcHeadersFrame(wireFormat, encoding, headers));
        dispatchFrameInbound(new DefaultGrpcMessageFrame(GrpcMessage.message(encoding, wireFormat, payload)));
        dispatchFrameInbound(new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, trailers));
        dispatchEndInbound();
      }

      return err;
    }
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    if (frame.type() == GrpcFrameType.CANCEL) {
      return sendCancel();
    } else {
      return outbound.write(frame);
    }
  }

  private Future<Void> onMessageWrite(GrpcMessage message) {
    State s = state;
    switch (s) {
      case OPENING:
        Promise<Void> promise = consumerContext.promise();
        enqueue(messageWrite(message, promise));
        return promise.future();
      case STREAMING:
        return writeMessage(message);
      default:
        return consumerContext.failedFuture(new IllegalStateException("Stream closed"));
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    ended = true;
    return outbound.end();
  }

  private InvalidStatusException handleFailure(Throwable cause, String encoding, WireFormat wireFormat) {
    GrpcStatus status = EventBusGrpcCodec.mapFailure(cause);
    emitFrameInbound(new DefaultGrpcHeadersFrame(wireFormat, encoding, MultiMap.caseInsensitiveMultiMap()));
    emitFrameInbound(new DefaultGrpcTrailersFrame(status, cause.getMessage(), MultiMap.caseInsensitiveMultiMap()));
    terminate();
    emitEndInbound();
    return new InvalidStatusException(GrpcStatus.OK, status);
  }

  @Override
  public void handle(TransportFrame frame, Message<Object> message) {
    switch (frame.getFrameCase()) {
      case HEADERS:
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);
        emitFrameInbound(new DefaultGrpcHeadersFrame(wireFormat, encoding, headers));
        break;
      case MESSAGE:
        emitFrameInbound(new DefaultGrpcMessageFrame(EventBusGrpcCodec.message(frame, encoding, wireFormat)));
        break;
      case TRAILERS:
        Trailers t = frame.getTrailers();
        MultiMap trailers = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(TRAILER_PREFIX, message.headers(), trailers);
        GrpcStatus status = Optional.ofNullable(GrpcStatus.valueOf(t.getStatus())).orElse(GrpcStatus.UNKNOWN);
        emitFrameInbound(new DefaultGrpcTrailersFrame(status, t.getStatusMessage().isEmpty() ? null : t.getStatusMessage(), trailers));
        terminate();
        emitEndInbound();
        break;
      case CANCEL:
        emitExceptionInbound(new CancellationException(frame.getCancel().getReason()));
        terminate();
        emitEndInbound();
        break;
      default:
        break;
    }
  }

  private Future<Void> sendCancel() {
    if (state == State.STREAMING) {
      sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code)));
    }
    terminate();
    return consumerContext.succeededFuture();
  }

  @Override
  public void close(Completable<Void> completion) {
    if (state != State.CLOSED) {
      if (state == State.STREAMING) {
        sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code).setReason("Client closed")));
      }
      terminate();
      emitExceptionInbound(new CancellationException("Client closed"));
      emitEndInbound();
    }
    completion.succeed();
  }

  @Override
  protected Future<Void> sendTransportFrame(TransportFrame.Builder builder) {
    Future<Void> sent = registration.sendTransportFrame(builder, wireFormat, null);
    if (sent != null) {
      sent.onFailure(this::handleRemoteEndpointDown);
    }
    return sent;
  }

  @Override
  void handleRemoteEndpointDown(Throwable cause) {
    if (state == State.CLOSED) {
      return;
    }

    boolean notifyRemoteEndpoint = state == State.STREAMING;

    terminate();

    if (notifyRemoteEndpoint) {
      sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code).setReason("Remote endpoint down")));
    }

    failPending(cause);
    emitExceptionInbound(cause);
    emitEndInbound();
  }

  private void failPending(Throwable cause) {
    failPendingWrites(cause);
  }

  private void terminate() {
    if (state == State.CLOSED) {
      return;
    }
    state = State.CLOSED;
    if (registration != null) {
      registration.unbind();
    }
  }

  private enum State {
    IDLE,
    OPENING,
    STREAMING,
    CLOSED
  }
}
