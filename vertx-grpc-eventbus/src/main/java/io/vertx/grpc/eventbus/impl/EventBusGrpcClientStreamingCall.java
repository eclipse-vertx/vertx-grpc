package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.Cancel;
import io.vertx.grpc.eventbus.transport.v1alpha.HalfClose;
import io.vertx.grpc.eventbus.transport.v1alpha.Trailers;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;
import io.vertx.grpc.eventbus.transport.v1alpha.WindowUpdate;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

class EventBusGrpcClientStreamingCall extends EventBusGrpcStreamBase implements FrameHandler {

  private final EventBusStreamEndpoint endpoint;
  private final EventBus eventBus;
  private final ServiceName serviceName;
  private final String methodName;
  private final Deque<GrpcMessage> pending = new ArrayDeque<>();

  private WireFormat wireFormat;
  private String encoding;
  private MultiMap requestHeaders;
  private Duration timeout;

  private boolean ended;
  private State state = State.IDLE;

  private String serverAddress;
  private long serverStreamId;

  private EventBusStreamEndpoint.StreamRegistration registration;

  public EventBusGrpcClientStreamingCall(ContextInternal context, EventBusStreamEndpoint endpoint, ServiceName serviceName, String methodName) {
    super(context, DEFAULT_WINDOW);
    this.endpoint = endpoint;
    this.eventBus = endpoint.eventBus();
    this.serviceName = serviceName;
    this.methodName = methodName;
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
        return open();
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
    if (state == State.STREAMING) {
      sendHalfClose();
    }
    return context.succeededFuture();
  }

  private void sendHalfClose() {
    sendTerminal(() -> sendTransportFrame(TransportFrame.newBuilder().setHalfClose(HalfClose.newBuilder())));
  }

  private Future<Void> open() {
    state = State.OPENING;

    WireFormat wireFormat = Optional.ofNullable(this.wireFormat).orElse(WireFormat.PROTOBUF);
    String encoding = Optional.ofNullable(this.encoding).orElse("identity");

    registration = endpoint.createStream();

    DeliveryOptions options = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, methodName)
      .addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name())
      .addHeader(EventBusHeaders.CLIENT_ADDRESS, endpoint.address())
      .addHeader(EventBusHeaders.CLIENT_STREAM_ID, Long.toString(registration.id()));

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
        promise.complete();
      } else {
        handleFailure(malformed, encoding, wireFormat);
        promise.fail(malformed);
      }
    });
    return promise.future();
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
    String serverStreamIdHeader = replyHeaders.get(EventBusHeaders.SERVER_STREAM_ID);
    String initialWindowHeader = replyHeaders.get(EventBusHeaders.INITIAL_WINDOW);

    if (serverAddress == null || serverStreamIdHeader == null || initialWindowHeader == null) {
      return new IllegalStateException("Malformed stream handshake reply: missing handshake headers");
    }

    long serverStreamId;
    int initialWindow;

    try {
      serverStreamId = Long.parseLong(serverStreamIdHeader);
      initialWindow = Integer.parseInt(initialWindowHeader);
    } catch (NumberFormatException e) {
      return new IllegalStateException("Malformed stream handshake reply: non-numeric handshake headers");
    }

    this.serverAddress = serverAddress;
    this.serverStreamId = serverStreamId;
    this.encoding = encoding;
    this.wireFormat = wireFormat;
    this.state = State.STREAMING;

    registration.bind(this);

    grantSendWindow(initialWindow);

    sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(window)));
    GrpcMessage message;
    while ((message = pending.poll()) != null) {
      writeMessage(message);
    }
    if (ended) {
      sendHalfClose();
    }

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
  protected void sendTransportFrame(TransportFrame.Builder builder) {
    if (serverAddress == null) {
      return;
    }
    builder.setStreamId(serverStreamId);
    eventBus.send(serverAddress, EventBusGrpcCodec.encodeFrame(builder));
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
