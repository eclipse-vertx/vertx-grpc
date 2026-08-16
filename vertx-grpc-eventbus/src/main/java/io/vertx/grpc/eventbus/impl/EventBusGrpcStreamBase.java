package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.eventbus.transport.v1alpha.Message;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;
import io.vertx.grpc.eventbus.transport.v1alpha.WindowUpdate;

import java.util.ArrayDeque;
import java.util.Deque;

abstract class EventBusGrpcStreamBase extends EventBusGrpcCallBase implements Closeable {

  static final int DEFAULT_WINDOW = 64;

  protected EventBusGrpcEndpoint.StreamRegistration registration;

  protected final int window;

  private final Deque<MessageWrite> outboundQueue = new ArrayDeque<>();

  private int granted;
  private int sendWindow;
  private long sequence;

  EventBusGrpcStreamBase(ContextInternal context, EventBusGrpcEndpoint.StreamRegistration registration, int window) {
    super(context);
    this.window = window;
    this.granted = window;
    this.registration = registration;
  }

  protected abstract Future<Void> sendTransportFrame(TransportFrame.Builder frame);

  abstract void handle(TransportFrame frame, io.vertx.core.eventbus.Message<Object> message);

  abstract void handleRemoteEndpointDown(Throwable cause);

  @Override
  protected void handleInboundMessage() {
    granted--;
    topUpWindow();
  }

  @Override
  protected void handleInboundFlowing() {
    topUpWindow();
  }

  private void topUpWindow() {
    if (flowing() && granted <= window / 2) {
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
    while ((head = outboundQueue.peek()) != null && !(head.flowControlled() && sendWindow <= 0)) {
      outboundQueue.poll().write();
    }
  }

  protected void grantSendWindow(int delta) {
    boolean wasFull = writeQueueFull();
    sendWindow += delta;
    drainOutbound();
    if (wasFull && !writeQueueFull()) {
      handleDrain();
    }
  }

  protected void failPendingWrites(Throwable cause) {
    MessageWrite write;
    while ((write = outboundQueue.poll()) != null) {
      write.fail(cause);
    }
  }

  @Override
  public boolean writeQueueFull() {
    return sendWindow <= 0 || !outboundQueue.isEmpty();
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
    public boolean flowControlled() {
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
