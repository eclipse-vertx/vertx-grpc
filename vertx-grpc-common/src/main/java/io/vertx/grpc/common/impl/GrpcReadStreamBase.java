/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.common.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageChannel;
import io.vertx.core.streams.ReadStream;
import io.vertx.grpc.common.*;

import static io.vertx.grpc.common.GrpcError.mapHttp2ErrorCode;

/**
 * Transforms {@code Buffer} into a stream of {@link GrpcMessage}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class GrpcReadStreamBase<S extends GrpcReadStreamBase<S, T>, T> implements GrpcReadStream<T>, Handler<Buffer> {

  static final GrpcMessage END_SENTINEL = new GrpcMessage() {
    @Override
    public String encoding() {
      return null;
    }
    @Override
    public WireFormat format() {
      return null;
    }
    @Override
    public Buffer payload() {
      return null;
    }
  };

  protected final ContextInternal context;
  private final String encoding;
  private final long maxMessageSize;
  private final WireFormat format;
  private final ReadStream<Buffer> stream;
  private final InboundMessageChannel<GrpcMessage> queue;
  private Buffer buffer;
  private long bytesToSkip;
  private Handler<Throwable> exceptionHandler;
  private Handler<GrpcMessage> messageHandler;
  private Handler<Void> endHandler;
  private Handler<InvalidMessageException> invalidMessageHandler;
  private GrpcMessage last;
  private final GrpcMessageDecoder<T> messageDecoder;
  private final Promise<Void> end;
  private GrpcWriteStreamBase<?, ?> ws;

  protected GrpcReadStreamBase(Context context,
                               ReadStream<Buffer> stream,
                               String encoding,
                               WireFormat format,
                               long maxMessageSize,
                               GrpcMessageDecoder<T> messageDecoder) {
    ContextInternal ctx = (ContextInternal) context;
    this.context = ctx;
    this.encoding = encoding;
    this.maxMessageSize = maxMessageSize;
    this.stream = stream;
    this.format = format;
    this.queue = new InboundMessageChannel<>(ctx.eventLoop(), ctx.executor(), 8, 16) {
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
          handleEnd();
        } else {
          GrpcReadStreamBase.this.handleMessage(msg);
        }
      }
    };
    this.messageDecoder = messageDecoder;
    this.end = ctx.promise();
  }

  public void init(GrpcWriteStreamBase<?, ?> ws) {
    this.ws = ws;
    stream.handler(this);
    stream.endHandler(v -> queue.write(END_SENTINEL));
    stream.exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        GrpcError error = mapHttp2ErrorCode(reset.getCode());
        ws.handleError(error);
      } else {
        handleException(err);
      }
    });
  }

  protected final T decodeMessage(GrpcMessage msg) throws CodecException {
    switch (msg.encoding()) {
      case "identity":
        // Nothing to do
        break;
      case "gzip": {
        msg = GrpcMessage.message("identity", msg.format(), Utils.GZIP_DECODER.apply(msg.payload()));
        break;
      }
      default:
        throw new UnsupportedOperationException();
    }
    return messageDecoder.decode(msg);
  }

  @Override
  public final WireFormat format() {
    return format;
  }

  @Override
  public final String encoding() {
    return encoding;
  }

  public final S pause() {
    queue.pause();
    return (S) this;
  }

  public final S resume() {
    return fetch(Long.MAX_VALUE);
  }

  public final S fetch(long amount) {
    queue.fetch(amount);
    return (S) this;
  }

  @Override
  public final S exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return (S) this;
  }

  @Override
  public final S errorHandler(@Nullable Handler<GrpcError> handler) {
    ws.errorHandler(handler);
    return (S) this;
  }

  @Override
  public final S messageHandler(Handler<GrpcMessage> handler) {
    messageHandler = handler;
    return (S) this;
  }

  @Override
  public final S invalidMessageHandler(@Nullable Handler<InvalidMessageException> handler) {
    invalidMessageHandler = handler;
    return (S) this;
  }

  @Override
  public S handler(@Nullable Handler<T> handler) {
    if (handler != null) {
      return messageHandler(msg -> {
        T decoded;
        try {
          decoded = decodeMessage(msg);
        } catch (CodecException e) {
          Handler<InvalidMessageException> errorHandler = invalidMessageHandler;
          if (errorHandler != null) {
            InvalidMessagePayloadException impe = new InvalidMessagePayloadException(msg, e);
            errorHandler.handle(impe);
          }
          return;
        }
        handler.handle(decoded);
      });
    } else {
      return messageHandler(null);
    }
  }

  @Override
  public final S endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return (S) this;
  }

  public void handle(Buffer chunk) {
    if (bytesToSkip > 0L) {
      int len = chunk.length();
      if (len <= bytesToSkip) {
        bytesToSkip -= len;
        return;
      }
      chunk = chunk.slice((int)bytesToSkip, len);
      bytesToSkip = 0L;
    }
    if (buffer == null) {
      buffer = chunk;
    } else {
      buffer.appendBuffer(chunk);
    }
    int idx = 0;
    while (true) {
      if (idx + 5 > buffer.length()) {
        break;
      }
      long len = ((long)buffer.getInt(idx + 1)) & 0xFFFFFFFFL;
      if (len > maxMessageSize) {
        Handler<InvalidMessageException> handler = invalidMessageHandler;
        if (handler != null) {
          MessageSizeOverflowException msoe = new MessageSizeOverflowException(len);
          context.dispatch(msoe, handler);
        }
        if (buffer.length() < (len + 5)) {
          bytesToSkip = (len + 5) - buffer.length();
          buffer = null;
          return;
        } else {
          buffer = buffer.slice((int)(len + 5), buffer.length());
          continue;
        }
      }
      if (len > buffer.length() - (idx + 5)) {
        break;
      }
      boolean compressed = buffer.getByte(idx) == 1;
      if (compressed && encoding == null) {
        throw new UnsupportedOperationException("Handle me");
      }
      Buffer payload = buffer.slice(idx + 5, (int)(idx + 5 + len));
      GrpcMessage message = GrpcMessage.message(compressed ? encoding : "identity", format, payload);
      queue.write(message);
      idx += 5 + len;
    }
    if (idx < buffer.length()) {
      buffer = buffer.getBuffer(idx, buffer.length());
    } else {
      buffer = null;
    }
  }

  public final void tryFail(Throwable err) {
    if (end.tryFail(err)) {
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        context.dispatch(err, handler);
      }
    }
  }

  protected final void handleException(Throwable err) {
    tryFail(err);
  }

  protected void handleEnd() {
    end.tryComplete();
    Handler<Void> handler = endHandler;
    if (handler != null) {
      context.dispatch(handler);
    }
  }

  private void handleMessage(GrpcMessage msg) {
    last = msg;
    Handler<GrpcMessage> handler = messageHandler;
    if (handler != null) {
      context.dispatch(msg, messageHandler);
    }
  }

  @Override
  public final Future<T> last() {
    return end()
      .map(v -> decodeMessage(last));
  }

  @Override
  public Future<Void> end() {
    return end.future();
  }
}
