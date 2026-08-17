package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.Message;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;
import io.vertx.grpc.eventbus.transport.v1alpha.WindowUpdate;

abstract class EventBusGrpcStreamBase implements GrpcStream, Closeable {

  static final int DEFAULT_WINDOW = 64;
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

  EventBusGrpcStreamBase(ContextInternal context, boolean localUnary, boolean remoteUnary, EventBusGrpcEndpoint.StreamRegistration registration, int window) {
    this.registration = registration;
    this.consumerContext = context;
    this.producerContext = registration.localEndpoint().producerContext;
    this.inboundQueue = new IMQ(registration, context);
    this.localUnary = localUnary;
    this.remoteUnary = remoteUnary;
    this.outboundQueue = new OMQ(context, window);
  }

  abstract void handle(TransportFrame frame, io.vertx.core.eventbus.Message<Object> message);

  abstract void handleRemoteEndpointDown(Throwable cause);

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
    assert producerContext.inThread();
    inboundQueue.write(o);
  }

  protected void emitFrameInbound(GrpcFrame frame) {
    emitInbound(frame);
  }

  protected void emitEndInbound() {
    emitInbound(END_MARKER);
  }

  protected void emitExceptionInbound(Throwable t) {
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

  protected Future<Void> writeMessage(GrpcMessage message) {
    Promise<Void> promise = consumerContext.promise();
    enqueue(messageWrite(message, promise));
    return promise.future();
  }

  protected MessageWrite messageWrite(GrpcMessage message, Promise<Void> promise) {
    return new MessageFrameWrite(this, message, promise);
  }

  private Future<Void> doSendMessage(GrpcMessage message) {
    return sendTransportFrame(TransportFrame.newBuilder()
      .setStreamSequence(++sequence)
      .setMessage(Message.newBuilder().setPayload(ByteString.copyFrom(message.payload().getBytes()))));
  }

  protected void enqueue(MessageWrite write) {
    outboundQueue.write(write);
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

  protected abstract Future<Void> sendTransportFrame(TransportFrame.Builder frame);

  private static final class MessageFrameWrite implements MessageWrite {

    private final EventBusGrpcStreamBase stream;
    private final GrpcMessage message;
    private final Promise<Void> promise;

    MessageFrameWrite(EventBusGrpcStreamBase stream, GrpcMessage message, Promise<Void> promise) {
      this.stream = stream;
      this.message = message;
      this.promise = promise;
    }

    @Override
    public void write() {
      stream.doSendMessage(message).onComplete(promise);
    }

    @Override
    public void fail(Throwable cause) {
      promise.fail(cause);
    }
  }

  public void updateOutboundWindow(int delta) {
    outboundQueue.updateWindow(delta);
  }

  private class IMQ extends InboundMessageQueue<Object> {

    private int window;

    public IMQ(EventBusGrpcEndpoint.StreamRegistration registration, ContextInternal context) {
      super(registration.localEndpoint().producerContext.executor(), context.executor());
      window = DEFAULT_WINDOW;
    }

    @Override
    protected void handleMessage(Object msg) {
      if (--window == 0) {
        window = DEFAULT_WINDOW;
        sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(DEFAULT_WINDOW)));
      }
      dispatchInbound(msg);
    }
  }

  private class OMQ extends OutboundMessageQueue<MessageWrite> {

    private long outboundInflight;

    public OMQ(ContextInternal context, int window) {
      super(context.eventLoop());

      this.outboundInflight = window;
    }

    @Override
    public boolean test(MessageWrite msg) {
      if (outboundInflight > 0) {
        outboundInflight--;
        msg.write();
        return true;
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
        msg.fail(cause);
      }
    }

    private void updateWindow(int delta) {
      outboundInflight += delta;
      outboundQueue.tryDrain();
    }
  }
}
