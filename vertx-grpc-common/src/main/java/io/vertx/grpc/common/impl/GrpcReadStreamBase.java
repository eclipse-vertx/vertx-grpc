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
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.streams.ReadStream;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcReadStream;

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
    public Buffer payload() {
      return null;
    }
  };

  protected final ContextInternal context;
  private final String encoding;
  private final ReadStream<Buffer> stream;
  private final InboundMessageQueue<GrpcMessage> queue;
  private Buffer buffer;
  private Handler<Throwable> exceptionHandler;
  private Handler<GrpcMessage> messageHandler;
  private Handler<Void> endHandler;
  private GrpcMessage last;
  private final GrpcMessageDecoder<T> messageDecoder;
  private final Promise<Void> end;
  private GrpcWriteStreamBase<?, ?> ws;

  protected GrpcReadStreamBase(Context context, ReadStream<Buffer> stream, String encoding, GrpcMessageDecoder<T> messageDecoder) {
    ContextInternal ctx = (ContextInternal) context;
    this.context = ctx;
    this.encoding = encoding;
    this.stream = stream;
    this.queue = new InboundMessageQueue<>(ctx.nettyEventLoop(), ctx, 8, 16) {
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
        msg = GrpcMessage.message("identity", GrpcMessageDecoder.GZIP.decode(msg));
        break;
      }
      default:
        throw new UnsupportedOperationException();
    }
    return messageDecoder.decode(msg);
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
  public final S endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return (S) this;
  }

  public void handle(Buffer chunk) {
    if (buffer == null) {
      buffer = chunk;
    } else {
      buffer.appendBuffer(chunk);
    }
    int idx = 0;
    int len;
    while (idx + 5 <= buffer.length() && (idx + 5 + (len = buffer.getInt(idx + 1)))<= buffer.length()) {
      boolean compressed = buffer.getByte(idx) == 1;
      if (compressed && encoding == null) {
        throw new UnsupportedOperationException("Handle me");
      }
      Buffer payload = buffer.slice(idx + 5, idx + 5 + len);
      GrpcMessage message = GrpcMessage.message(compressed ? encoding : "identity", payload);
      queue.write(message);
      idx += 5 + len;
    }
    if (idx < buffer.length()) {
      buffer = buffer.getBuffer(idx, buffer.length());
    } else {
      buffer = null;
    }
  }

  protected final void handleException(Throwable err) {
    end.tryFail(err);
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      context.dispatch(err, handler);
    }
  }

  protected void handleEnd() {
    end.tryComplete();
    Handler<Void> handler = endHandler;
    if (handler != null) {
      context.dispatch(handler);
    }
  }

  protected void handleMessage(GrpcMessage msg) {
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
