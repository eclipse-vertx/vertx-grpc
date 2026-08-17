package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;
import io.vertx.grpc.eventbus.transport.v1alpha.WindowUpdate;

abstract class EventBusGrpcCallBase implements GrpcStream {

  static final int DEFAULT_WINDOW = 64;

  static final Object END_MARKER = new Object();

  protected final EventBusGrpcEndpoint.StreamRegistration registration;
  protected final ContextInternal consumerContext;
  protected final ContextInternal producerContext;

  private final InboundMessageQueue<Object> inboundQueue;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private int window = DEFAULT_WINDOW;

  EventBusGrpcCallBase(EventBusGrpcEndpoint.StreamRegistration registration, ContextInternal consumerContext) {
    InboundMessageQueue<Object> queue = new InboundMessageQueue<>(registration.localEndpoint().producerContext.executor(), consumerContext.executor()) {
      @Override
      protected void handleResume() {
      }

      @Override
      protected void handlePause() {
      }

      @Override
      protected void handleMessage(Object msg) {
        if (--window == 0) {
          // Replenish
          window = DEFAULT_WINDOW;
          sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(DEFAULT_WINDOW)));
        }
        dispatchInbound(msg);
      }

      @Override
      protected void handleDispose(Object msg) {
        //
      }
    };

    this.registration = registration;
    this.consumerContext = consumerContext;
    this.producerContext = registration.localEndpoint().producerContext;
    this.inboundQueue = queue;
  }

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

  protected abstract Future<Void> sendTransportFrame(TransportFrame.Builder frame);

}
