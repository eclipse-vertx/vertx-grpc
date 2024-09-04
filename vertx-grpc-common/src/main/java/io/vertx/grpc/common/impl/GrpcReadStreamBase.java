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
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.impl.InboundBuffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.MessageSizeOverflowException;
import io.vertx.grpc.common.InvalidMessageException;
import io.vertx.grpc.common.InvalidMessagePayloadException;

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
  private final long maxMessageSize;
  private final ReadStream<Buffer> stream;
  private final InboundBuffer<GrpcMessage> queue;
  private Buffer buffer;
  private long bytesToSkip;
  private Handler<GrpcError> errorHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<GrpcMessage> messageHandler;
  private Handler<Void> endHandler;
  private Handler<InvalidMessageException> invalidMessageHandler;
  private GrpcMessage last;
  private final GrpcMessageDecoder<T> messageDecoder;
  private final Promise<Void> end;

  protected GrpcReadStreamBase(Context context, ReadStream<Buffer> stream, String encoding, long maxMessageSize, GrpcMessageDecoder<T> messageDecoder) {
    this.context = (ContextInternal) context;
    this.encoding = encoding;
    this.maxMessageSize = maxMessageSize;
    this.stream = stream;
    this.queue = new InboundBuffer<>(context);
    this.messageDecoder = messageDecoder;
    this.end = ((ContextInternal) context).promise();
  }

  public void init() {
    stream.handler(this);
    stream.endHandler(v -> queue.write(END_SENTINEL));
    stream.exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        handleReset(((StreamResetException)err).getCode());
      } else {
        handleException(err);
      }
    });
    queue.drainHandler(v -> stream.resume());
    queue.handler(msg -> {
      if (msg == END_SENTINEL) {
        handleEnd();
      } else {
        handleMessage(msg);
      }
    });
  }

  private T decodeMessage(GrpcMessage msg) throws CodecException {
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

  public final void handle(Buffer chunk) {
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
    boolean pause = false;
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
      GrpcMessage message = GrpcMessage.message(compressed ? encoding : "identity", payload);
      pause |= !queue.write(message);
      idx += 5 + len;
    }
    if (pause) {
      stream.pause();
    }
    if (idx < buffer.length()) {
      buffer = buffer.getBuffer(idx, buffer.length());
    } else {
      buffer = null;
    }
  }

  public final S pause() {
    queue.pause();
    return (S) this;
  }

  public final S resume() {
    queue.resume();
    return (S) this;
  }

  public final S fetch(long amount) {
    queue.fetch(amount);
    return (S) this;
  }

  @Override
  public final S errorHandler(Handler<GrpcError> handler) {
    errorHandler = handler;
    return (S) this;
  }

  @Override
  public final S exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
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
  public final S handler(@Nullable Handler<T> handler) {
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
  public S endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return (S) this;
  }

  protected void handleReset(long code) {
    Handler<GrpcError> handler = errorHandler;
    if (handler != null) {
      GrpcError error = mapHttp2ErrorCode(code);
      if (error != null) {
        handler.handle(error);
      }
    }
  }

  protected void handleException(Throwable err) {
    end.tryFail(err);
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(err);
    }
  }

  protected void handleEnd() {
    end.tryComplete();
    Handler<Void> handler = endHandler;
    if (handler != null) {
      handler.handle(null);
    }
  }

  private void handleMessage(GrpcMessage msg) {
    last = msg;
    Handler<GrpcMessage> handler = messageHandler;
    if (handler != null) {
      handler.handle(msg);
    }
  }

  @Override
  public Future<T> last() {
    return end()
      .map(v -> decodeMessage(last));
  }

  @Override
  public Future<Void> end() {
    return end.future();
  }
}
