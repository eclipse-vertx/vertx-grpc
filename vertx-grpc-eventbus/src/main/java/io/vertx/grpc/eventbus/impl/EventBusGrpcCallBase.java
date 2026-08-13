package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcFrameType;
import io.vertx.grpc.common.impl.GrpcInboundStream;
import io.vertx.grpc.common.impl.GrpcOutboundStream;
import io.vertx.grpc.common.impl.GrpcStream;

import java.util.ArrayDeque;
import java.util.Deque;

abstract class EventBusGrpcCallBase implements GrpcStream {

  private static final Object END_MARKER = new Object();

  protected final ContextInternal context;

  private final Deque<Object> inboundQueue = new ArrayDeque<>();
  private boolean draining;
  private boolean flowing = true;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> drainHandler;

  EventBusGrpcCallBase(ContextInternal context) {
    this.context = context;
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
  public GrpcOutboundStream drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    return this;
  }

  @Override
  public GrpcOutboundStream setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return false;
  }

  @Override
  public GrpcInboundStream pause() {
    flowing = false;
    return this;
  }

  @Override
  public GrpcInboundStream resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public GrpcInboundStream fetch(long amount) {
    if (amount > 0) {
      flowing = true;
      drainInbound();
      handleInboundFlowing();
    }
    return this;
  }

  protected final boolean flowing() {
    return flowing;
  }

  protected void emit(GrpcFrame frame) {
    inboundQueue.add(frame);
    drainInbound();
  }

  protected void emitEnd() {
    inboundQueue.add(END_MARKER);
    drainInbound();
  }

  protected void emitException(Throwable t) {
    inboundQueue.add(t);
    drainInbound();
  }

  private void drainInbound() {
    if (draining) {
      return;
    }
    draining = true;
    try {
      while (flowing && !inboundQueue.isEmpty()) {
        dispatchInbound(inboundQueue.poll());
      }
    } finally {
      draining = false;
    }
  }

  private void dispatchInbound(Object event) {
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
      if (frame.type() == GrpcFrameType.MESSAGE) {
        handleInboundMessage();
      }
    }
  }

  /**
   * Notify the drain handler, to be called by subclasses when the write queue drained.
   */
  protected void handleDrain() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      context.runOnContext(v -> handler.handle(null));
    }
  }

  /**
   * Called after a message frame has been dispatched to the frame handler.
   */
  protected void handleInboundMessage() {
  }

  /**
   * Called when the inbound stream resumes, after the queued events have been dispatched.
   */
  protected void handleInboundFlowing() {
  }
}
