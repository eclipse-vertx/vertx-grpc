package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcInboundStream;
import io.vertx.grpc.common.impl.GrpcOutboundStream;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.eventbus.transport.v1alpha.Message;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;
import io.vertx.grpc.eventbus.transport.v1alpha.WindowUpdate;

import java.util.ArrayDeque;
import java.util.Deque;

abstract class EventBusGrpcStreamBase implements GrpcStream, Closeable {

  static final int DEFAULT_WINDOW = 64;

  protected final ContextInternal context;
  protected final int window;

  private final Deque<MessageWrite> outboundQueue = new ArrayDeque<>();

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> drainHandler;

  private boolean flowing = true;
  private int granted;
  private int sendWindow;
  private Runnable pendingTerminal;
  private long sequence;

  EventBusGrpcStreamBase(ContextInternal context, int window) {
    this.context = context;
    this.window = window;
    this.granted = window;
  }

  protected abstract Future<Void> sendTransportFrame(TransportFrame.Builder frame);

  abstract void handle(TransportFrame frame, io.vertx.core.eventbus.Message<Object> message);

  protected void onInboundMessage() {
    granted--;
    topUpWindow();
  }

  private void topUpWindow() {
    if (flowing && granted <= window / 2) {
      int delta = window - granted;
      if (delta > 0) {
        granted += delta;
        sendTransportFrame(TransportFrame.newBuilder().setWindowUpdate(WindowUpdate.newBuilder().setDelta(delta)));
      }
    }
  }

  protected Future<Void> writeMessage(GrpcMessage message) {
    if (sendWindow > 0 && outboundQueue.isEmpty()) {
      return doSendMessage(message);
    }
    MessageWrite write = new MessageWrite(message, context.promise());
    outboundQueue.add(write);
    return write.promise.future();
  }

  private Future<Void> doSendMessage(GrpcMessage message) {
    sendWindow--;
    return sendTransportFrame(TransportFrame.newBuilder()
      .setStreamSequence(++sequence)
      .setMessage(Message.newBuilder().setPayload(ByteString.copyFrom(message.payload().getBytes()))));
  }

  protected void sendTerminal(Runnable send) {
    if (outboundQueue.isEmpty()) {
      send.run();
      onTerminalSent();
    } else {
      pendingTerminal = send;
    }
  }

  protected void onTerminalSent() {
  }

  protected void grantSendWindow(int delta) {
    boolean wasFull = writeQueueFull();
    sendWindow += delta;

    while (sendWindow > 0 && !outboundQueue.isEmpty()) {
      MessageWrite write = outboundQueue.poll();
      doSendMessage(write.message).onComplete(write.promise);
    }

    if (outboundQueue.isEmpty() && pendingTerminal != null) {
      Runnable terminal = pendingTerminal;
      pendingTerminal = null;
      terminal.run();
      onTerminalSent();
    }

    if (wasFull && !writeQueueFull()) {
      Handler<Void> h = drainHandler;
      if (h != null) {
        context.runOnContext(v -> h.handle(null));
      }
    }
  }

  @Override
  public boolean writeQueueFull() {
    return sendWindow <= 0 || !outboundQueue.isEmpty();
  }

  @Override
  public GrpcOutboundStream setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public GrpcOutboundStream drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    return this;
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
      topUpWindow();
    }
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

  protected void emitException(Throwable t) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(t);
    }
  }

  static class MessageWrite {

    final GrpcMessage message;
    final Promise<Void> promise;

    MessageWrite(GrpcMessage message, Promise<Void> promise) {
      this.message = message;
      this.promise = promise;
    }
  }
}
