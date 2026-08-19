package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.*;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

abstract class EventBusGrpcStreamBase implements GrpcStream, Closeable {

  static final Object END_MARKER = new Object();

  protected final EventBusGrpcEndpoint.StreamRegistration registration;
  protected final ContextInternal consumerContext;
  protected final ContextInternal producerContext;
  protected final boolean localUnary;
  protected final boolean remoteUnary;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> drainHandler;

  private final OMQ outboundQueue;
  private final IMQ inboundQueue;

  private long sequence;

  EventBusGrpcStreamBase(ContextInternal context, boolean localUnary, boolean remoteUnary,
                         EventBusGrpcEndpoint.StreamRegistration registration, int initialInboundWindowSize,
                         int initialOutboundWindowSize) {
    this.registration = registration;
    this.consumerContext = context;
    this.producerContext = registration.localEndpoint().producerContext;
    this.inboundQueue = new IMQ(registration, context, initialInboundWindowSize);
    this.localUnary = localUnary;
    this.remoteUnary = remoteUnary;
    this.outboundQueue = new OMQ(context, initialOutboundWindowSize);
    this.sequence = 1;
  }

  abstract void handle(TransportFrame frame, io.vertx.core.eventbus.Message<Object> message);

  abstract void handleRemoteEndpointDown(Throwable cause);

  abstract WireFormat format();

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

  private void emitInbound(Object o) {
    producerContext.execute(o, inboundQueue::write);
  }

  protected final void emitFrameInbound(GrpcFrame frame) {
    emitInbound(frame);
  }

  protected final void emitEndInbound() {
    emitInbound(END_MARKER);
  }

  protected final void emitExceptionInbound(Throwable t) {
    emitInbound(t);
  }

  protected void dispatchFrameInbound(GrpcFrame frame) {
    dispatchInbound(frame);
  }

  protected void dispatchEndInbound() {
    dispatchInbound(END_MARKER);
  }

  private void dispatchInbound(Object event) {
    assert consumerContext.inThread();
    if (event == END_MARKER) {
      Handler<Void> handler = endHandler;
      if (handler != null) {
        handler.handle(null);
      }
    } else if (event instanceof Throwable) {
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        handler.handle((Throwable) event);
      }
    } else {
      GrpcFrame frame = (GrpcFrame) event;
      Handler<GrpcFrame> handler = frameHandler;
      if (handler != null) {
        handler.handle(frame);
      }
    }
  }

  protected MessageWrite messageWrite(GrpcMessage message) {
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

    return new MessageWrite(completion, builder, null);
  }

  final MessageWrite trailersWrite(GrpcTrailersFrame frame) {
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
    return new MessageWrite(completion, builder, deliveryOptions);
  }

  protected MessageWrite halfCloseWrite() {
    return new MessageWrite(
      consumerContext.promise(),
      TransportFrame.newBuilder().setHalfClose(HalfClose.newBuilder()),
      null);
  }

  protected Future<Void> enqueue(MessageWrite write) {
    write.frame.setStreamId(sequence++);
    outboundQueue.write(write);
    return write.completion.future();
  }

  private Throwable cause;

  protected void failPendingWrites(Throwable cause) {
    this.cause = cause;
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
    Future<Void> sent = registration.sendTransportFrame(builder, format(), options);
    if (sent != null) {
      sent.onFailure(this::handleRemoteEndpointDown);
    }
    return sent;
  }

  Future<Void> sendTransportFrame(TransportFrame.Builder builder) {
    Future<Void> sent = registration.sendTransportFrame(builder, format(), null);
    if (sent != null) {
      sent.onFailure(this::handleRemoteEndpointDown);
    }
    return sent;
  }

  public void updateOutboundWindow(int delta) {
    outboundQueue.updateWindow(delta);
  }

  private class IMQ extends InboundMessageQueue<Object> {

    private final int initialWindowSize;
    private int windowSize;

    public IMQ(EventBusGrpcEndpoint.StreamRegistration registration, ContextInternal context, int initialWindowSize) {
      super(registration.localEndpoint().producerContext.executor(), context.executor());
      this.initialWindowSize = initialWindowSize;
      this.windowSize = initialWindowSize;
    }

    @Override
    protected void handleMessage(Object msg) {
      if (--windowSize < initialWindowSize / 2) {
        // Replenish window
        int windowSizeUpdate = initialWindowSize - windowSize;
        windowSize = initialWindowSize;
        sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(windowSizeUpdate)));
      }
      dispatchInbound(msg);
    }
  }

  private class OMQ extends OutboundMessageQueue<MessageWrite> {

    private final ContextInternal context;
    private long window;

    public OMQ(ContextInternal context, int initialWindowSize) {
      super(context.executor());

      this.context = context;
      this.window = initialWindowSize;
    }

    private boolean writeFrame(MessageWrite write)  {

      TransportFrame.Builder frame = write.frame;

      WireFormat format = format();

      Future<Void> sent = registration.sendTransportFrame(frame, format, write.deliveryOptions);
      if (sent != null) {
        sent.onFailure(EventBusGrpcStreamBase.this::handleRemoteEndpointDown);
        sent.onComplete(write.completion);
      }

      return sent != null;
    }


    @Override
    public boolean test(MessageWrite msg) {
      if (window > 0) {
        boolean written;
        written = writeFrame(msg);
        if (written) {
          window--;
        }
        return written;
      } else {
        return false;
      }
    }

    @Override
    protected void handleDrained() {
      Handler<Void> handler = drainHandler;
      if (handler != null) {
        handler.handle(null);
      }
    }

    @Override
    protected void handleDispose(MessageWrite msg) {
      Throwable c = cause;
      if (c != null) {
        msg.completion.tryFail(cause);
      }
    }

    private void updateWindow(int delta) {
      if (context.inThread()) {
        window += delta;
        outboundQueue.tryDrain();
      } else {
        context.execute(delta, this::updateWindow);
      }
    }
  }
}
