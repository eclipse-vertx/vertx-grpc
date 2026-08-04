package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
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

  private final EventBusGrpcEndpoint.StreamRegistration registration;
  private final long clientStreamId;
  private final WireFormat wireFormat;
  private final String encoding;
  private final MessageProducer<Object> producer;

  private boolean clientListening;
  private MultiMap pendingHeaders;
  private Promise<Void> pendingHeadersPromise;
  private Future<Void> lastWrite;
  private boolean closed;

  public EventBusGrpcServerStreamingCall(
    ContextInternal context,
    EventBus eventBus,
    EventBusGrpcEndpoint.StreamRegistration registration,
    String clientAddress,
    long clientStreamId,
    WireFormat wireFormat,
    String encoding,
    int window
  ) {
    super(context, window);
    this.registration = registration;
    this.clientStreamId = clientStreamId;
    this.wireFormat = wireFormat;
    this.encoding = encoding;
    this.producer = eventBus.sender(clientAddress, new DeliveryOptions().addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name()));
  }

  @Override
  public void handle(TransportFrame frame, Message<Object> message) {
    if (!clientListening) {
      clientListening = true;
      Promise<Void> promise = pendingHeadersPromise;
      if (promise != null) {
        MultiMap headers = pendingHeaders;
        pendingHeaders = null;
        pendingHeadersPromise = null;
        sendResponseHeaders(headers).onComplete(promise);
      }
    }

    switch (frame.getFrameCase()) {
      case MESSAGE:
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
        terminate();
        emitException(new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED));
        break;
      default:
        break;
    }
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    Future<Void> written;
    switch (frame.type()) {
      case HEADERS:
        MultiMap responseHeaders = ((GrpcHeadersFrame) frame).headers();
        if (clientListening) {
          written = sendResponseHeaders(responseHeaders);
        } else {
          pendingHeaders = responseHeaders;
          pendingHeadersPromise = context.promise();
          written = pendingHeadersPromise.future();
        }
        break;
      case MESSAGE:
        written = writeMessage(((GrpcMessageFrame) frame).message());
        break;
      case TRAILERS:
        Promise<Void> promise = context.promise();
        enqueue(new TrailersWrite((GrpcTrailersFrame) frame, promise));
        written = promise.future();
        break;
      default:
        return context.failedFuture("Invalid message: " + frame.type());
    }
    lastWrite = written;
    return written;
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    Future<Void> last = lastWrite;
    if (last == null) {
      return context.failedFuture(new IllegalStateException("Cannot end a stream that did not write any frame"));
    }
    return last;
  }

  private Future<Void> sendResponseHeaders(MultiMap headers) {
    DeliveryOptions options = new DeliveryOptions();
    if (headers != null && !headers.isEmpty()) {
      MultiMap delivery = MultiMap.caseInsensitiveMultiMap();
      EventBusHeaders.encodeMultiMap(HEADER_PREFIX, headers, delivery);
      options.setHeaders(delivery);
    }
    options.addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name());
    return sendTransportFrame(TransportFrame.newBuilder().setHeaders(Headers.newBuilder()), options);
  }

  private Future<Void> sendTrailers(GrpcTrailersFrame frame) {
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
    return sendTransportFrame(TransportFrame.newBuilder().setTrailers(trailers), options);
  }

  @Override
  public void close(Completable<Void> completion) {
    if (!closed) {
      sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.UNAVAILABLE.code).setReason("Server closed")));
      terminate();
      GrpcErrorException failure = new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED);
      failPending(failure);
      emitException(failure);
    }
    completion.succeed();
  }

  @Override
  protected Future<Void> sendTransportFrame(TransportFrame.Builder builder) {
    return sendTransportFrame(builder, null);
  }

  private Future<Void> sendTransportFrame(TransportFrame.Builder builder, DeliveryOptions options) {
    builder.setStreamId(clientStreamId);
    Object payload = EventBusGrpcCodec.encodeFrame(builder, wireFormat);
    Future<Void> sent = options != null ? producer.write(payload, options) : producer.write(payload);
    sent.onFailure(this::handleRemoteEndpointDown);
    return sent;
  }

  @Override
  void handleRemoteEndpointDown(Throwable cause) {
    if (closed) {
      return;
    }
    terminate();
    sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.UNAVAILABLE.code).setReason("Remote endpoint down")));
    failPending(cause);
    emitException(new GrpcErrorException(GrpcError.UNAVAILABLE, GrpcStatus.UNAVAILABLE));
  }

  private void failPending(Throwable cause) {
    Promise<Void> promise = pendingHeadersPromise;
    if (promise != null) {
      pendingHeaders = null;
      pendingHeadersPromise = null;
      promise.fail(cause);
    }
    failPendingWrites(cause);
  }

  private void terminate() {
    if (closed) {
      return;
    }
    closed = true;
    registration.unbind();
  }

  private final class TrailersWrite implements MessageWrite {

    private final GrpcTrailersFrame frame;
    private final Promise<Void> promise;

    TrailersWrite(GrpcTrailersFrame frame, Promise<Void> promise) {
      this.frame = frame;
      this.promise = promise;
    }

    @Override
    public void write() {
      Future<Void> sent = sendTrailers(frame);
      terminate();
      sent.onComplete(promise);
    }

    @Override
    public void fail(Throwable cause) {
      promise.fail(cause);
    }
  }
}
