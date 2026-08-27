package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.eventbus.transport.v1alpha.*;

import java.util.List;
import java.util.Optional;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

abstract class EventBusGrpcStreamBase<E extends EventBusGrpcEndpoint> extends EventBusGrpcEndpoint.StreamRegistration implements GrpcStream {

  protected final E localEndpoint;
  protected final ContextInternal consumerContext;
  protected final ContextInternal producerContext;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<GrpcError> errorHandler;
  private Handler<Void> drainHandler;

  private final OMQ outboundQueue;
  private final IMQ inboundQueue;

  private long outboundSequence;
  private long inboundSequence;

  boolean closed;
  Throwable closeCause;
  private Throwable pendingWriteFailureCause;

  EventBusGrpcStreamBase(E localEndpoint, long id, ContextInternal context, int initialInboundWindowSize, int initialOutboundWindowSize) {
    super(localEndpoint, id);
    this.localEndpoint = localEndpoint;
    this.consumerContext = context;
    this.producerContext = localEndpoint.producerContext();
    this.inboundQueue = new IMQ(context, initialInboundWindowSize);
    this.outboundQueue = new OMQ(localEndpoint.producerContext().executor(), initialOutboundWindowSize);
    this.outboundSequence = 1;
    this.inboundSequence = 1;
  }

  abstract static class Client extends EventBusGrpcStreamBase<EventBusGrpcClientEndpoint> {

    protected final boolean localUnary;
    protected final boolean remoteUnary;

    public Client(EventBusGrpcClientEndpoint localEndpoint, long id, ContextInternal context, boolean localUnary, boolean remoteUnary, int initialInboundWindowSize, int initialOutboundWindowSize) {
      super(localEndpoint, id, context, initialInboundWindowSize, initialOutboundWindowSize);

      this.localUnary = localUnary;
      this.remoteUnary = remoteUnary;
    }

    Future<Void> connect(Object body, MultiMap requestHeaders, ServiceName serviceName, String methodName,
                         long pingTimeout, String encoding, WireFormat wireFormat, java.time.Duration timeout) {
      return enqueue(createConnectWrite(body, requestHeaders, serviceName, methodName, pingTimeout, encoding, wireFormat, timeout));
    }

    private OutboundWrite createConnectWrite(Object body, MultiMap requestHeaders, ServiceName serviceName,
                                             String methodName, long pingTimeout, String encoding, WireFormat wireFormat,
                                             java.time.Duration timeout) {
      DeliveryOptions options = new DeliveryOptions();

      if (localUnary) {
        if (!remoteUnary) {
          options.addHeader(EventBusHeaders.STREAM_INITIAL_WINDOW, "" + localEndpoint.initialWindowSize);
          options.addHeader(EventBusHeaders.ENDPOINT_WIRE_FORMAT, wireFormat.name());
          options.addHeader(EventBusHeaders.ENDPOINT_ADDRESS, localEndpoint.address());
        }
      } else {
        options.addHeader(EventBusHeaders.ENDPOINT_WIRE_FORMAT, wireFormat.name());
        options.addHeader(EventBusHeaders.ENDPOINT_ADDRESS, localEndpoint.address());
        if (!remoteUnary) {
          options.addHeader(EventBusHeaders.STREAM_INITIAL_WINDOW, "" + localEndpoint.initialWindowSize);
        }
        if (pingTimeout > 0) {
          options.addHeader(EventBusHeaders.ENDPOINT_PING_TIMEOUT, Long.toString(pingTimeout));
        }
      }

      if (timeout != null) {
        options.setSendTimeout(timeout.toMillis());
      }

      options.addHeader(EventBusHeaders.SERVICE_PROXY_ACTION, methodName);;
      options.addHeader(EventBusHeaders.STREAM_METHOD_NAME, methodName);;
      options.addHeader(EventBusHeaders.STREAM_WIRE_FORMAT, wireFormat.name());
      options.addHeader(EventBusHeaders.STREAM_ID, Long.toString(id()));

      if (requestHeaders != null) {
        EventBusHeaders.encodeMultiMap(HEADER_PREFIX, requestHeaders, options.getHeaders());
      }

      Promise<Void> promise = consumerContext.promise();

      return new OutboundWrite(promise) {
        @Override
        long sequence() {
          return 0L;
        }
        @Override
        void write() {
          registerStream();
          localEndpoint.request(serviceName.fullyQualifiedName(), body, options)
            .onComplete(ar -> {
              if (closed) {
                Throwable cause = closeCause;
                if (cause == null) {
                  cause = new VertxException("Stream closed");
                }
                promise.fail(cause);
              } else {
                if (ar.succeeded()) {
                  Throwable malformed = handleReply(ar.result());
                  if (malformed == null) {
                    promise.succeed();
                  } else {
                    InvalidStatusException err = invalidStatusException(malformed);
                    close(err, true);
                    promise.fail(err);
                  }
                } else {
                  InvalidStatusException err = invalidStatusException(ar.cause());
                  close(err, false);
                  promise.fail(err);
                }
              }
            });
        }

        private InvalidStatusException invalidStatusException(Throwable cause) {
          GrpcStatus status = EventBusGrpcCodec.mapFailure(cause);
          return new InvalidStatusException(GrpcStatus.OK, status);
        }

        private Throwable handleReply(io.vertx.core.eventbus.Message<Object> reply) {

          WireFormat serverFormat;
          String serverAddress;
          if (!localUnary || !remoteUnary) {
            MultiMap replyHeaders = reply.headers();
            serverAddress = replyHeaders.get(EventBusHeaders.ENDPOINT_ADDRESS);
            String s = replyHeaders.get(EventBusHeaders.ENDPOINT_WIRE_FORMAT);
            if (s == null) {
              return new IllegalStateException("Malformed handshake reply: missing endpoint-wire-format header");
            }
            if (serverAddress == null) {
              return new IllegalStateException("Malformed handshake reply: missing grpc-endpoint-address header");
            }
            switch (s) {
              case "json":
                serverFormat = WireFormat.JSON;
                break;
              case "proto":
                serverFormat = WireFormat.PROTOBUF;
                break;
              default:
                return new IllegalStateException("Malformed handshake reply: invalid endpoint-wire-format header");
            }
          } else {
            serverAddress = null;
            serverFormat = null;
          }

          int initialOutboundWindowSize;
          if (remoteUnary) {
            initialOutboundWindowSize = EventBusGrpcServerOptions.DEFAULT_INITIAL_WINDOW_SIZE;
          } else {
            String initialWindowHeader = reply.headers().get(EventBusHeaders.STREAM_INITIAL_WINDOW);
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

          if (serverAddress != null) {
            registerRemoteEndpoint(serverAddress, pingTimeout, serverFormat);
          }

          updateOutboundWindow(initialOutboundWindowSize);

          if (remoteUnary && localUnary) {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            MultiMap trailers = MultiMap.caseInsensitiveMultiMap();
            EventBusHeaders.decodeMultimap(HEADER_PREFIX, reply.headers(), headers);
            EventBusHeaders.decodeMultimap(TRAILER_PREFIX, reply.headers(), trailers);
            Buffer payload = EventBusGrpcCodec.decodeBody(reply.body());
            emitInbound(List.of(
              new DefaultGrpcHeadersFrame(wireFormat, encoding, headers),
              new DefaultGrpcMessageFrame(GrpcMessage.message(encoding, wireFormat, payload)),
              new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, trailers)
            ));
          }

          return null;
        }
      };
    }
  }

  @Override
  protected final void handleProducerClosed(Throwable cause) {
    closeCause = cause;
    closed = true;
    consumerContext.execute(cause, err -> {
      if (cause != null) {
        failPendingWrites(cause);
        if (cause instanceof GrpcErrorException) {
          handleError(((GrpcErrorException)cause).error());
        } else {
          handleException(cause);
        }
      }
      handleConsumerClosed();
    });
  }

  protected void handleError(GrpcError error) {
    consumerContext.dispatch(error, e -> {
      Handler<GrpcError> handler = errorHandler;
      if (handler != null) {
        handler.handle(e);
      }
    });
  }

  protected void handleException(Throwable t) {
    consumerContext.dispatch(t, e -> {
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        handler.handle(e);
      }
    });
  }

  void handle(TransportFrame frame, io.vertx.core.eventbus.Message<TransportFrame> message) {
    long sequence;
    if ((sequence = frame.getStreamSequence()) > 0 && sequence != inboundSequence++) {
      close(GrpcError.CANCELLED, true);
    } else {
      switch (frame.getFrameCase()) {
        case WINDOW_UPDATE:
          updateOutboundWindow(frame.getWindowUpdate().getDelta());
          break;
        case HEADERS:
          MultiMap headers = MultiMap.caseInsensitiveMultiMap();
          EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);
          emitInbound(new DefaultGrpcHeadersFrame(format(), encoding(), headers));
          break;
        case MESSAGE:
          emitInbound(new DefaultGrpcMessageFrame(EventBusGrpcCodec.message(frame, encoding(), format())));
          break;
        case HALF_CLOSE:
          emitInbound(DefaultGrpcHalfCloseFrame.INSTANCE);
          closeInbound();
          break;
        case TRAILERS:
          Trailers t = frame.getTrailers();
          MultiMap trailers = MultiMap.caseInsensitiveMultiMap();
          EventBusHeaders.decodeMultimap(TRAILER_PREFIX, message.headers(), trailers);
          GrpcStatus status = Optional.ofNullable(GrpcStatus.valueOf(t.getStatus())).orElse(GrpcStatus.UNKNOWN);
          emitInbound(new DefaultGrpcTrailersFrame(status, t.getStatusMessage().isEmpty() ? null : t.getStatusMessage(), trailers));
          closeInbound();
          break;
        default:
          break;
      }
    }
  }

  abstract void handleConsumerClosed();

  abstract WireFormat format();

  abstract String encoding();

  @Override
  public GrpcStream handler(Handler<GrpcFrame> handler) {
    this.frameHandler = handler;
    return this;
  }

  @Override
  public GrpcStream endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public GrpcStream exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public GrpcStream errorHandler(Handler<GrpcError> handler) {
    this.errorHandler = handler;
    return this;
  }

  @Override
  public GrpcOutboundStream setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public GrpcInboundStream pause() {
    inboundQueue.pause();
    return this;
  }

  @Override
  public GrpcInboundStream resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public GrpcInboundStream fetch(long amount) {
    inboundQueue.fetch(amount);
    return this;
  }

  protected final void emitInbound(GrpcFrame frame) {
    inboundQueue.write(frame);
  }

  protected final void emitInbound(List<GrpcFrame> frames) {
    inboundQueue.write(frames);
  }

  protected void dispatchInbound(GrpcFrame frame) {
    Handler<GrpcFrame> handler = frameHandler;
    if (handler != null) {
      handler.handle(frame);
    }
    if (frame.type() == GrpcFrameType.HALF_CLOSE) {
      Handler<Void> endHandler = this.endHandler;
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
  }

  protected OutboundWrite frameWrite(GrpcFrame frame) {
    switch (frame.type()) {
      case MESSAGE:
        return messageFrameWrite(((GrpcMessageFrame)frame).message());
      case HALF_CLOSE:
        if (frame instanceof GrpcTrailersFrame) {
          return trailersFrameWrite((GrpcTrailersFrame) frame);
        } else {
          return halfCloseFrameWrite();
        }
      default:
        throw new IllegalArgumentException();
    }
  }

  private OutboundFrameWrite messageFrameWrite(GrpcMessage message) {
    Promise<Void> completion = consumerContext.promise();

    Message.Builder messageBuilder;
    switch (message.format().name()) {
      case "proto":
        messageBuilder = Message.newBuilder().setBytes(ByteString.copyFrom(message.payload().getBytes()));
        break;
      case "json":
        messageBuilder = Message.newBuilder().setStringBytes(ByteString.copyFrom(message.payload().getBytes()));
        break;
      default:
        throw new UnsupportedOperationException();
    }
    TransportFrame.Builder builder = TransportFrame
      .newBuilder()
      .setMessage(messageBuilder);

    return new OutboundFrameWrite(completion, builder, outboundSequence++, null);
  }

  private OutboundFrameWrite trailersFrameWrite(GrpcTrailersFrame frame) {
    Promise<Void> completion = consumerContext.promise();
    DeliveryOptions deliveryOptions = new DeliveryOptions();
    MultiMap headers = frame.trailers();
    if (headers != null && !headers.isEmpty()) {
      MultiMap delivery = MultiMap.caseInsensitiveMultiMap();
      EventBusHeaders.encodeMultiMap(TRAILER_PREFIX, headers, delivery);
      deliveryOptions.setHeaders(delivery);
    }
    Trailers.Builder trailersBuilder = Trailers.newBuilder().setStatus(frame.status().code);
    if (frame.statusMessage() != null) {
      trailersBuilder.setStatusMessage(frame.statusMessage());
    }
    TransportFrame.Builder builder = TransportFrame
      .newBuilder()
      .setTrailers(trailersBuilder);
    return new OutboundFrameWrite(completion, builder, outboundSequence++, deliveryOptions);
  }

  private OutboundFrameWrite halfCloseFrameWrite() {
    return new OutboundFrameWrite(
      consumerContext.promise(),
      TransportFrame.newBuilder().setHalfClose(HalfClose.newBuilder()),
      outboundSequence++,
      null);
  }

  Future<Void> enqueue(OutboundWrite write) {
    outboundQueue.write(write);
    return write.completion.future();
  }

  final Future<Void> enqueue(GrpcFrame frame) {
    return enqueue(frameWrite(frame));
  }

  protected void failPendingWrites(Throwable cause) {
    this.pendingWriteFailureCause = cause;
    outboundQueue.close();
  }

  @Override
  public boolean writeQueueFull() {
    return !outboundQueue.isWritable();
  }

  @Override
  public GrpcOutboundStream drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    return this;
  }

  Future<Void> sendTransportFrame(TransportFrame.Builder builder, DeliveryOptions options) {
    if (producerContext.inThread()) {
      return sendTransportFrame(builder, format(), options);
    } else {
      PromiseInternal<Void> ret = consumerContext.promise();
      producerContext.execute(() -> {
        Future<Void> result = sendTransportFrame(builder, format(), options);
        result.onComplete(ret);
      });
      return ret.future();
    }
  }

  void updateOutboundWindow(int delta) {
    outboundQueue.updateWindow(delta);
  }

  private class IMQ extends InboundMessageQueue<GrpcFrame> {

    private final int initialWindowSize;
    private int windowSize;

    public IMQ(ContextInternal context, int initialWindowSize) {
      super(producerContext.executor(), context.executor());
      this.initialWindowSize = initialWindowSize;
      this.windowSize = initialWindowSize;
    }

    @Override
    protected void handleMessage(GrpcFrame msg) {
      if (--windowSize < initialWindowSize / 2) {
        // Replenish window
        int windowSizeUpdate = initialWindowSize - windowSize;
        windowSize = initialWindowSize;
        sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(windowSizeUpdate)), null);
      }
      dispatchInbound(msg);
    }
  }

  private class OMQ extends OutboundMessageQueue<OutboundWrite> {

    private long window;

    public OMQ(EventExecutor eventExecutor, int initialWindowSize) {
      super(eventExecutor);

      this.window = initialWindowSize;
    }

    @Override
    public boolean test(OutboundWrite msg) {
      if (window > 0) {
        msg.write();
        window--;
        return true;
      } else {
        return false;
      }
    }

    @Override
    protected void handleDrained() {
      consumerContext.emit(v -> {
        Handler<Void> handler = drainHandler;
        if (handler != null) {
          consumerContext.dispatch(null, handler);
        }
      });
    }

    @Override
    protected void handleDispose(OutboundWrite msg) {
      Throwable c = pendingWriteFailureCause;
      if (c != null) {
        msg.cancel(c);
      }
    }

    private void updateWindow(int delta) {
      if (producerContext.inThread()) {
        window += delta;
        outboundQueue.tryDrain();
      } else {
        producerContext.execute(delta, this::updateWindow);
      }
    }
  }

  static abstract class OutboundWrite {

    final Promise<Void> completion;

    public OutboundWrite(Promise<Void> completion) {
      this.completion = completion;
    }

    abstract long sequence();

    abstract void write();

    void cancel(Throwable cause) {
      completion.tryFail(cause);
    }
  }

  private class OutboundFrameWrite extends OutboundWrite {


    final long sequence;
    final TransportFrame.Builder frame;
    final DeliveryOptions deliveryOptions;

    public OutboundFrameWrite(Promise<Void> completion, TransportFrame.Builder frame, long sequence, DeliveryOptions deliveryOptions) {
      super(completion);
      this.frame = frame;
      this.deliveryOptions = deliveryOptions;
      this.sequence = sequence;
    }

    @Override
    long sequence() {
      return sequence;
    }

    @Override
    public void write() {
      frame.setStreamSequence(sequence);
      Future<Void> sent = sendTransportFrame(frame, deliveryOptions);
      sent.onComplete(completion);
    }
  }
}
