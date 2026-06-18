package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.Handler;
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

abstract class EventBusGrpcStreamBase implements GrpcStream {

  static final int DEFAULT_WINDOW = 64;

  protected final ContextInternal context;
  protected final int window;

  private final Deque<GrpcMessage> outboundQueue = new ArrayDeque<>();

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

  protected abstract void sendTransportFrame(TransportFrame.Builder frame);

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

  protected void writeMessage(GrpcMessage message) {
    if (sendWindow > 0 && outboundQueue.isEmpty()) {
      doSendMessage(message);
    } else {
      outboundQueue.add(message);
    }
  }

  private void doSendMessage(GrpcMessage message) {
    sendWindow--;
    sendTransportFrame(TransportFrame.newBuilder()
      .setSeq(++sequence)
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
      doSendMessage(outboundQueue.poll());
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
}
