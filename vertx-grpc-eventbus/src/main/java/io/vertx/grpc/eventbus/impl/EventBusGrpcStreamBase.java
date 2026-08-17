package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.ByteString;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.impl.GrpcOutboundStream;
import io.vertx.grpc.eventbus.transport.v1alpha.Message;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

abstract class EventBusGrpcStreamBase extends EventBusGrpcCallBase implements Closeable {

  protected final boolean localUnary;
  protected final boolean remoteUnary;

  private Handler<Void> drainHandler;

  private final OutboundMessageQueue<MessageWrite> outboundQueue;

  private long sequence;

  private long outboundInflight;

  EventBusGrpcStreamBase(ContextInternal context, boolean localUnary, boolean remoteUnary, EventBusGrpcEndpoint.StreamRegistration registration, int window) {
    super(registration, context);
    this.localUnary = localUnary;
    this.remoteUnary = remoteUnary;
    this.outboundInflight = window;
    this.outboundQueue = new OutboundMessageQueue<>(context.eventLoop()) {
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
    };
  }

  abstract void handle(TransportFrame frame, io.vertx.core.eventbus.Message<Object> message);

  abstract void handleRemoteEndpointDown(Throwable cause);

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

  protected void grantSendWindow(int delta) {
    outboundInflight += delta;
    outboundQueue.tryDrain();
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
