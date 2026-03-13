package io.vertx.grpc.common.impl;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.streams.ReadStream;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.MessageSizeOverflowException;

import static io.vertx.grpc.common.impl.GrpcReadStreamBase.END_SENTINEL;

public class GrpcInboundInvoker implements Handler<Buffer>, GrpcInboundFlowControl {

  private final ReadStream<Buffer> stream;
  private final GrpcMessageDeframer deframer;
  private final InboundMessageQueue<GrpcMessage> queue;
  private GrpcReadStreamBase<?, ?> grpcReadStreamBase;

  public GrpcInboundInvoker(ContextInternal ctx, ReadStream<Buffer> stream, GrpcMessageDeframer deframer) {
    this.stream = stream;
    this.deframer = deframer;
    this.queue = new InboundMessageQueue<>(ctx.executor(), ctx.executor(), 8, 16) {
      @Override
      protected void handleResume() {
        stream.resume();
      }
      @Override
      protected void handlePause() {
        stream.pause();
      }
      @Override
      protected void handleMessage(GrpcMessage msg) {
        if (msg == END_SENTINEL) {
          grpcReadStreamBase.handleEnd();
        } else {
          grpcReadStreamBase.handleMessage(msg);
        }
      }
    };
  }

  public void init(GrpcReadStreamBase<?, ?> grpc, long maxMessageSize) {
    grpcReadStreamBase = grpc;
    deframer.maxMessageSize(maxMessageSize);
    stream.handler(this);
    stream.endHandler(v -> {
      deframer.end();
      deframe();
      queue.write(END_SENTINEL);
    });
    stream.exceptionHandler(err -> {
      grpcReadStreamBase.handleException(err);
    });

  }

  public void handle(Buffer chunk) {
    deframer.update(chunk);
    deframe();
  }

  private void deframe() {
    while (true) {
      Object ret = deframer.next();
      if (ret == null) {
        break;
      } else if (ret instanceof MessageSizeOverflowException) {
        MessageSizeOverflowException msoe = (MessageSizeOverflowException) ret;
        grpcReadStreamBase.handleInvalidMessage(msoe);
      } else {
        GrpcMessage msg = (GrpcMessage) ret;
        queue.write(msg);
      }
    }
  }

  @Override
  public void pause() {
    queue.pause();
  }

  @Override
  public void fetch(long amount) {
    queue.fetch(amount);
  }
}
