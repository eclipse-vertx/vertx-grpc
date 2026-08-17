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

class EventBusGrpcClientStreamingCall extends EventBusGrpcStreamBase {

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

  private Outbound outbound;

  public EventBusGrpcClientStreamingCall(ContextInternal context, boolean localUnary, boolean remoteUnary, EventBusGrpcEndpoint.StreamRegistration registration, EventBusGrpcEndpoint endpoint, ServiceName serviceName, String methodName) {
    super(context, localUnary, remoteUnary, registration, DEFAULT_WINDOW);
    this.endpoint = endpoint;
    this.eventBus = endpoint.eventBus();
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.pending = new ArrayDeque<>();
    this.state = State.IDLE;
    this.outbound = new StreamingOutbound();
  }

  abstract class Outbound {
    abstract Future<Void> write(GrpcFrame frame);
    abstract Future<Void> end();
  }

  class StreamingOutbound extends Outbound {
    @Override
    Future<Void> write(GrpcFrame frame) {
      switch (frame.type()) {
        case HEADERS:
          GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
          wireFormat = headersFrame.format();
          encoding = headersFrame.encoding();
          requestHeaders = headersFrame.headers();
          timeout = headersFrame.timeout();
          return open();
        case MESSAGE:
          return onMessageWrite(((GrpcMessageFrame) frame).message());
        case CANCEL:
          return sendCancel();
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

      WireFormat wireFormat = Optional.ofNullable(EventBusGrpcClientStreamingCall.this.wireFormat).orElse(WireFormat.PROTOBUF);
      String encoding = Optional.ofNullable(EventBusGrpcClientStreamingCall.this.encoding).orElse("identity");

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

        Throwable malformed = handleInitialized(ar.result(), encoding, wireFormat);
        if (malformed == null) {

          MessageWrite write;
          while ((write = pending.poll()) != null) {
            enqueue(write);
          }
          if (ended) {
            sendHalfClose();
          }

          promise.complete();
        } else {
          handleFailure(malformed, encoding, wireFormat);
          promise.fail(malformed);
        }
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

  @Override
  public Future<Void> write(GrpcFrame frame) {
    return outbound.write(frame);
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

  private void handleFailure(Throwable cause, String encoding, WireFormat wireFormat) {
    GrpcStatus status = EventBusGrpcCodec.mapFailure(cause);
    emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, MultiMap.caseInsensitiveMultiMap()));
    emit(new DefaultGrpcTrailersFrame(status, cause.getMessage(), MultiMap.caseInsensitiveMultiMap()));
    terminate();
    emitEnd();
  }

  private Throwable handleInitialized(Message<Object> reply, String encoding, WireFormat wireFormat) {
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

    this.encoding = encoding;
    this.wireFormat = wireFormat;
    this.state = State.STREAMING;

    registration.bind(this, serverAddress, endpoint.pingTimeout());

    grantSendWindow(initialWindow);

    sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(window)));

    return null;
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
