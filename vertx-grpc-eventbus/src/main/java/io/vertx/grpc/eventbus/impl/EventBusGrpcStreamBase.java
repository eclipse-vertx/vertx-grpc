package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcFrameType;
import io.vertx.grpc.common.impl.GrpcInboundStream;
import io.vertx.grpc.common.impl.GrpcOutboundStream;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.eventbus.transport.v1alpha.Heartbeat;
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

  private static final Object END_MARKER = new Object();
  private final Deque<Object> inboundQueue = new ArrayDeque<>();
  private boolean draining;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> drainHandler;

  private boolean flowing = true;
  private int granted;
  private int sendWindow;
  private long sequence;

  private long heartbeatInterval;
  private long idleTimeout;
  private long heartbeatTimerId = -1L;
  private long idleExpirationTimestamp = Long.MAX_VALUE;

  EventBusGrpcStreamBase(ContextInternal context, int window) {
    this.context = context;
    this.window = window;
    this.granted = window;
  }

  protected void configureLiveness(long heartbeatInterval, long idleTimeout) {
    this.heartbeatInterval = heartbeatInterval;
    this.idleTimeout = idleTimeout;
  }

  protected abstract Future<Void> sendTransportFrame(TransportFrame.Builder frame);

  abstract void handle(TransportFrame frame, io.vertx.core.eventbus.Message<Object> message);

  private void onInboundMessage() {
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
    Promise<Void> promise = context.promise();
    enqueue(messageWrite(message, promise));
    return promise.future();
  }

  protected MessageWrite messageWrite(GrpcMessage message, Promise<Void> promise) {
    return new MessageFrameWrite(this, message, promise);
  }

  private Future<Void> doSendMessage(GrpcMessage message) {
    sendWindow--;
    return sendTransportFrame(TransportFrame.newBuilder()
      .setStreamSequence(++sequence)
      .setMessage(Message.newBuilder().setPayload(ByteString.copyFrom(message.payload().getBytes()))));
  }

  protected void enqueue(MessageWrite write) {
    outboundQueue.add(write);
    drainOutbound();
  }

  private void drainOutbound() {
    MessageWrite head;
    while ((head = outboundQueue.peek()) != null && !(head.windowed() && sendWindow <= 0)) {
      outboundQueue.poll().write();
    }
  }

  protected void grantSendWindow(int delta) {
    boolean wasFull = writeQueueFull();
    sendWindow += delta;
    drainOutbound();
    if (wasFull && !writeQueueFull()) {
      Handler<Void> h = drainHandler;
      if (h != null) {
        context.runOnContext(v -> h.handle(null));
      }
    }
  }

  protected void failPendingWrites(Throwable cause) {
    MessageWrite write;
    while ((write = outboundQueue.poll()) != null) {
      write.fail(cause);
    }
  }

  protected abstract void handleIdleTimeout();

  protected void startHeartbeat() {
    if (heartbeatInterval > 0 && heartbeatTimerId < 0) {
      heartbeatTimerId = context.owner().setPeriodic(heartbeatInterval, id ->
        sendTransportFrame(TransportFrame.newBuilder().setHeartbeat(Heartbeat.newBuilder())));
    }
  }

  protected void stopHeartbeat() {
    if (heartbeatTimerId >= 0) {
      context.owner().cancelTimer(heartbeatTimerId);
      heartbeatTimerId = -1L;
    }
  }

  protected void startIdleTimeout() {
    if (idleTimeout > 0) {
      idleExpirationTimestamp = System.currentTimeMillis() + idleTimeout;
    }
  }

  protected void resetIdleTimeout() {
    if (idleExpirationTimestamp != Long.MAX_VALUE) {
      idleExpirationTimestamp = System.currentTimeMillis() + idleTimeout;
    }
  }

  protected void cancelIdleTimeout() {
    idleExpirationTimestamp = Long.MAX_VALUE;
  }

  boolean expired(long now) {
    return now >= idleExpirationTimestamp;
  }

  protected void stopLiveness() {
    stopHeartbeat();
    cancelIdleTimeout();
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
      drainInbound();
      topUpWindow();
    }
    return this;
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
        onInboundMessage();
      }
    }
  }

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
    public boolean windowed() {
      return true;
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
}
