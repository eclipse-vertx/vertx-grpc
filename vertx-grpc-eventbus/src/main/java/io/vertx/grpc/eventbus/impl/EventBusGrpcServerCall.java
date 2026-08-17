package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.Cancel;
import io.vertx.grpc.eventbus.transport.v1alpha.Headers;
import io.vertx.grpc.eventbus.transport.v1alpha.Trailers;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

class EventBusGrpcServerCall extends EventBusGrpcStreamBase {

  private final WireFormat wireFormat;
  private final String encoding;

  private boolean closed;

  private final Inbound inbound;
  private final Outbound outbound;

  public EventBusGrpcServerCall(
    ContextInternal context,
    boolean localUnary,
    boolean remoteUnary,
    EventBusGrpcEndpoint.StreamRegistration registration,
    WireFormat wireFormat,
    String encoding,
    int window) {
    super(context, localUnary, remoteUnary, registration, window);
    this.wireFormat = wireFormat;
    this.encoding = encoding;
    this.inbound = remoteUnary ? new UnaryInbound() : new StreamingInbound();
    this.outbound = localUnary && remoteUnary ? new UnaryOutbound() : new StreamingOutbound();
  }

  private interface Inbound {

    void init(MultiMap headers, Message<Object> message);

  }

  private class UnaryInbound implements Inbound {
    @Override
    public void init(MultiMap headers, Message<Object> message) {
      Buffer payload = EventBusGrpcCodec.decodeBody(message.body());
      emitFrameInbound(new DefaultGrpcHeadersFrame(wireFormat, "identity", headers));
      emitFrameInbound(new DefaultGrpcMessageFrame(GrpcMessage.message("identity", wireFormat, payload)));
      emitEndInbound();
    }
  }

  private class StreamingInbound implements Inbound {
    @Override
    public void init(MultiMap headers, Message<Object> message) {
      GrpcHeadersFrame frame = new DefaultGrpcHeadersFrame(wireFormat, "identity, ", headers);
      emitFrameInbound(frame);
    }
  }

  private interface Outbound {
    void init(String address, Message<Object> msg);
    Future<Void> write(GrpcFrame frame);
    Future<Void> end();
  }

  private class UnaryOutbound implements Outbound {

    private Message<Object> message;
    private MultiMap headers;
    private GrpcMessage encodedMessage;
    private boolean replied;

    @Override
    public void init(String address, Message<Object> msg) {
      this.message = msg;
    }

    @Override
    public Future<Void> write(GrpcFrame frame) {
      switch (frame.type()) {
        case HEADERS:
          headers = ((GrpcHeadersFrame) frame).headers();
          return consumerContext.succeededFuture();
        case MESSAGE:
          encodedMessage = ((GrpcMessageFrame) frame).message();
          return consumerContext.succeededFuture();
        case TRAILERS:
          assert !replied;
          replied = true;
          GrpcTrailersFrame trailersFrame = (GrpcTrailersFrame) frame;
          return handleTrailers(trailersFrame.status(), trailersFrame.statusMessage(), encodedMessage, headers, trailersFrame.trailers());
        default:
          return consumerContext.succeededFuture();
      }
    }

    @Override
    public Future<Void> end() {
      return consumerContext.succeededFuture();
    }

    private Future<Void> handleTrailers(GrpcStatus status, String statusMessage, GrpcMessage grpcMsg, MultiMap headers, MultiMap trailers) {
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
        Buffer payload = grpcMsg != null ? grpcMsg.payload() : Buffer.buffer();
        options.setHeaders(multiMap);
        message.reply(EventBusGrpcCodec.encodeBody(payload, wireFormat), options);
      }
      return consumerContext.succeededFuture();
    }
  }

  private class StreamingOutbound implements Outbound {

    private Future<Void> lastWrite;

    @Override
    public void init(String address, Message<Object> msg) {
      DeliveryOptions replyOptions = new DeliveryOptions()
        .addHeader(EventBusHeaders.SERVER_ADDRESS, address)
        .addHeader(EventBusHeaders.INITIAL_WINDOW, Integer.toString(DEFAULT_WINDOW));

      msg.reply(Buffer.buffer(), replyOptions);
    }

    @Override
    public Future<Void> write(GrpcFrame frame) {
      Future<Void> written;
      switch (frame.type()) {
        case HEADERS:
          MultiMap responseHeaders = ((GrpcHeadersFrame) frame).headers();
          written = sendResponseHeaders(responseHeaders);
          break;
        case MESSAGE:
          written = writeMessage(((GrpcMessageFrame) frame).message());
          break;
        case TRAILERS:
          Promise<Void> promise = consumerContext.promise();
          enqueue(new TrailersWrite((GrpcTrailersFrame) frame, promise));
          written = promise.future();
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
  }

  @Override
  public void handle(TransportFrame frame, Message<Object> message) {
    switch (frame.getFrameCase()) {
      case MESSAGE:
        emitFrameInbound(new DefaultGrpcMessageFrame(EventBusGrpcCodec.message(frame, encoding, wireFormat)));
        break;
      case HALF_CLOSE:
        emitEndInbound();
        break;
      case CANCEL:
        if (closed) {
          break;
        }
        terminate();
        emitExceptionInbound(new GrpcErrorException(GrpcError.CANCELLED, GrpcStatus.CANCELLED));
        break;
      default:
        break;
    }
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    return outbound.write(frame);
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    return outbound.end();
  }

  void init(String address, Message<Object> message) {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);
    outbound.init(address, message);
    inbound.init(headers, message);
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
      emitExceptionInbound(failure);
    }
    completion.succeed();
  }

  @Override
  protected Future<Void> sendTransportFrame(TransportFrame.Builder builder) {
    return sendTransportFrame(builder, null);
  }

  private Future<Void> sendTransportFrame(TransportFrame.Builder builder, DeliveryOptions options) {
    Future<Void> sent = registration.sendTransportFrame(builder, wireFormat, options);
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
    emitExceptionInbound(new GrpcErrorException(GrpcError.UNAVAILABLE, GrpcStatus.UNAVAILABLE));
  }

  private void failPending(Throwable cause) {
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
