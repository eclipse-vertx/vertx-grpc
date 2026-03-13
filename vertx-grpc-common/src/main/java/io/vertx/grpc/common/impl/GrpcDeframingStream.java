package io.vertx.grpc.common.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.streams.ReadStream;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.MessageSizeOverflowException;

import static io.vertx.grpc.common.impl.GrpcReadStreamBase.END_SENTINEL;

/**
 * A stream that deframes a stream of gRPC messages.
 */
public class GrpcDeframingStream implements ReadStream<GrpcMessage> {

  private final ReadStream<Buffer> stream;
  private final GrpcMessageDeframer deframer;
  private final InboundMessageQueue<GrpcMessage> queue;
  private Handler<GrpcMessage> messageHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;

  public GrpcDeframingStream(ContextInternal ctx, ReadStream<Buffer> stream, GrpcMessageDeframer deframer) {
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
          Handler<Void> handler = endHandler;
          if (handler != null) {
            handler.handle(null);
          }
        } else {
          Handler<GrpcMessage> handler = messageHandler;
          if (handler != null) {
            handler.handle(msg);
          }
        }
      }
    };
  }

  public void init(GrpcReadStreamBase<?, ?> grpc, long maxMessageSize) {
    deframer.maxMessageSize(maxMessageSize);
    stream.handler(this::handle);
    stream.endHandler(v -> {
      deframer.end();
      deframe();
      queue.write(END_SENTINEL);
    });
    stream.exceptionHandler(err -> {
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        handler.handle(err);
      }
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
        Handler<Throwable> handler = exceptionHandler;
        if (handler != null) {
          handler.handle(msoe);
        }
      } else {
        GrpcMessage msg = (GrpcMessage) ret;
        queue.write(msg);
      }
    }
  }

  @Override
  public GrpcDeframingStream pause() {
    queue.pause();
    return this;
  }

  @Override
  public GrpcDeframingStream fetch(long amount) {
    queue.fetch(amount);
    return this;
  }

  @Override
  public GrpcDeframingStream exceptionHandler(@Nullable Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public GrpcDeframingStream handler(@Nullable Handler<GrpcMessage> handler) {
    this.messageHandler = handler;
    return this;
  }

  @Override
  public GrpcDeframingStream resume() {
    queue.fetch(Long.MAX_VALUE);
    return this;
  }

  @Override
  public GrpcDeframingStream endHandler(@Nullable Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }
}
