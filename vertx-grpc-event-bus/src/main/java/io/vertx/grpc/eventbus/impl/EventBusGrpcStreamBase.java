package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.impl.*;

abstract class EventBusGrpcStreamBase implements GrpcStream {

  protected final ContextInternal context;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;

  EventBusGrpcStreamBase(ContextInternal context) {
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
    return this;
  }

  @Override
  public GrpcInboundStream pause() {
    return this;
  }

  @Override
  public GrpcInboundStream resume() {
    return this;
  }

  @Override
  public GrpcInboundStream fetch(long amount) {
    return this;
  }

  protected void emit(GrpcFrame frame) {
    Handler<GrpcFrame> handler = frameHandler;
    if (handler != null) {
      handler.handle(frame);
    }
  }

  protected void emitEnd() {
    Handler<Void> handler = endHandler;
    if (handler != null) {
      handler.handle(null);
    }
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
  public GrpcOutboundStream drainHandler(Handler<Void> handler) {
    return this;
  }
}
