package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.*;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

class EventBusGrpcClientCall extends EventBusGrpcStreamBase {

  private final EventBusGrpcEndpoint endpoint;
  private final EventBus eventBus;
  private final ServiceName serviceName;
  private final String methodName;
  private final Deque<MessageWrite> pending;

  private WireFormat wireFormat;
  private String encoding;
  private MultiMap requestHeaders;
  private Duration timeout;

  private boolean ended;
  private State state;

  private final Outbound outbound;
  private final Inbound inbound;

  public EventBusGrpcClientCall(ContextInternal context, boolean localUnary, boolean remoteUnary, EventBusGrpcEndpoint.StreamRegistration registration, EventBusGrpcEndpoint endpoint, ServiceName serviceName, String methodName) {
    super(context, localUnary, remoteUnary, registration, DEFAULT_WINDOW);
    this.endpoint = endpoint;
    this.eventBus = endpoint.eventBus();
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.pending = new ArrayDeque<>();
    this.state = State.IDLE;
    this.outbound = localUnary ? new UnaryOutbound() : new StreamingOutbound();
    this.encoding = "identity";
    this.wireFormat = WireFormat.PROTOBUF;
    this.inbound = remoteUnary && localUnary ? new UnaryInbound() : new StreamingInbound();
  }

  abstract class Outbound {
    abstract Future<Void> write(GrpcFrame frame);
    abstract Future<Void> end();
  }

  class UnaryOutbound extends Outbound {

    private GrpcMessage message;

    @Override
    Future<Void> write(GrpcFrame frame) {
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
          return context.succeededFuture();
        case MESSAGE:
          message = ((GrpcMessageFrame) frame).message();
          return context.succeededFuture();
        default:
          return context.failedFuture("Frame not handled");
      }
    }

    @Override
    public Future<Void> end() {
      GrpcMessage msg = message;
      if (msg == null) {
        return context.failedFuture("No message to send");
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

      if (timeout != null) {
        options.setSendTimeout(timeout.toMillis());
      }

      if (requestHeaders != null) {
        EventBusHeaders.encodeMultiMap(HEADER_PREFIX, requestHeaders, options.getHeaders());
      }

      Buffer payload = message != null ? message.payload() : Buffer.buffer();
      Object body = EventBusGrpcCodec.encodeBody(payload, wireFormat);

      Promise<Void> promise = context.promise();

      eventBus.request(serviceName.fullyQualifiedName(), body, options).onComplete(ar -> {
        if (ar.succeeded()) {
          Throwable malformed = inbound.handleReply(ar.result(), encoding, wireFormat);
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

  class StreamingOutbound extends Outbound {
    @Override
    Future<Void> write(GrpcFrame frame) {
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
          return context.failedFuture("Invalid message: " + frame.type());
      }
    }

    @Override
    public Future<Void> end() {
      if (state == State.STREAMING) {
        sendHalfClose();
      }
      return context.succeededFuture();
    }

    private Future<Void> open() {
      state = State.OPENING;

      DeliveryOptions options = new DeliveryOptions()
        .addHeader(EventBusHeaders.ACTION, methodName)
        .addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name())
        .addHeader(EventBusHeaders.CLIENT_ADDRESS, endpoint.address())
        .addHeader(EventBusHeaders.STREAM_ID, Long.toString(registration.id()));

      if (endpoint.pingTimeout() > 0) {
        options.addHeader(EventBusHeaders.PING_TIMEOUT, Long.toString(endpoint.pingTimeout()));
      }

      if (timeout != null) {
        options.setSendTimeout(timeout.toMillis());
      }

      if (requestHeaders != null) {
        EventBusHeaders.encodeMultiMap(HEADER_PREFIX, requestHeaders, options.getHeaders());
      }

      Promise<Void> promise = context.promise();
      eventBus.request(serviceName.fullyQualifiedName(), Buffer.buffer(), options).onComplete(ar -> {
        if (ar.failed()) {
          handleFailure(ar.cause(), encoding, wireFormat);
          promise.fail(ar.cause());
          return;
        }

        Throwable malformed = inbound.handleReply(ar.result(), encoding, wireFormat);
        if (malformed == null) {

          MessageWrite write;
          while ((write = pending.poll()) != null) {
            enqueue(write);
          }
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
      public void write() {
        sendTransportFrame(TransportFrame.newBuilder().setHalfClose(HalfClose.newBuilder()));
      }
    }
  }

  abstract class Inbound {

    abstract Throwable handleReply(Message<Object> reply, String encoding, WireFormat wireFormat);

  }

  class StreamingInbound extends Inbound {

    @Override
    Throwable handleReply(Message<Object> reply, String encoding, WireFormat wireFormat) {
      MultiMap replyHeaders = reply.headers();
      String serverAddress = replyHeaders.get(EventBusHeaders.SERVER_ADDRESS);
      String initialWindowHeader = replyHeaders.get(EventBusHeaders.INITIAL_WINDOW);

      if (serverAddress == null || initialWindowHeader == null) {
        return new IllegalStateException("Malformed stream handshake reply: missing handshake headers");
      }

      int initialWindow;

      try {
        initialWindow = Integer.parseInt(initialWindowHeader);
      } catch (NumberFormatException e) {
        return new IllegalStateException("Malformed stream handshake reply: non-numeric handshake headers");
      }

      EventBusGrpcClientCall.this.encoding = encoding;
      EventBusGrpcClientCall.this.wireFormat = wireFormat;
      EventBusGrpcClientCall.this.state = State.STREAMING;

      registration.bind(EventBusGrpcClientCall.this, serverAddress, endpoint.pingTimeout());

      grantSendWindow(initialWindow);

      sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(window)));

      return null;
    }
  }

  class UnaryInbound extends Inbound {

    @Override
    Throwable handleReply(Message<Object> reply, String encoding, WireFormat wireFormat) {
      MultiMap headers = MultiMap.caseInsensitiveMultiMap();
      MultiMap trailers = MultiMap.caseInsensitiveMultiMap();
      EventBusHeaders.decodeMultimap(HEADER_PREFIX, reply.headers(), headers);
      EventBusHeaders.decodeMultimap(TRAILER_PREFIX, reply.headers(), trailers);
      Buffer payload = EventBusGrpcCodec.decodeBody(reply.body());
      emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, headers));
      emit(new DefaultGrpcMessageFrame(GrpcMessage.message(encoding, wireFormat, payload)));
      emit(new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, trailers));
      emitEnd();
      return null;
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
    switch (state) {
      case OPENING:
        Promise<Void> promise = context.promise();
        pending.add(messageWrite(message, promise));
        return promise.future();
      case STREAMING:
        return writeMessage(message);
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
    return outbound.end();
  }

  private InvalidStatusException handleFailure(Throwable cause, String encoding, WireFormat wireFormat) {
    GrpcStatus status = EventBusGrpcCodec.mapFailure(cause);
    emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, MultiMap.caseInsensitiveMultiMap()));
    emit(new DefaultGrpcTrailersFrame(status, cause.getMessage(), MultiMap.caseInsensitiveMultiMap()));
    terminate();
    emitEnd();
    return new InvalidStatusException(GrpcStatus.OK, status);
  }

  @Override
  public void handle(TransportFrame frame, Message<Object> message) {
    switch (frame.getFrameCase()) {
      case HEADERS:
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);
        emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, headers));
        break;
      case MESSAGE:
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
        terminate();
        emitEnd();
        break;
      case CANCEL:
        emitException(new CancellationException(frame.getCancel().getReason()));
        terminate();
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
    terminate();
    return context.succeededFuture();
  }

  @Override
  public void close(Completable<Void> completion) {
    if (state != State.CLOSED) {
      if (state == State.STREAMING) {
        sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code).setReason("Client closed")));
      }
      terminate();
      emitException(new CancellationException("Client closed"));
      emitEnd();
    }
    completion.succeed();
  }

  @Override
  protected Future<Void> sendTransportFrame(TransportFrame.Builder builder) {
    Future<Void> sent = registration.sendTransportFrame(builder, wireFormat, null);
    sent.onFailure(this::handleRemoteEndpointDown);
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
    emitException(cause);
    emitEnd();
  }

  private void failPending(Throwable cause) {
    failPendingWrites(cause);
    MessageWrite write;
    while ((write = pending.poll()) != null) {
      write.fail(cause);
    }
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
