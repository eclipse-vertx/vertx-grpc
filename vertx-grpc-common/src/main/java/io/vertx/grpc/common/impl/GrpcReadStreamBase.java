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
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;

/**
 * Transforms {@code Buffer} into a stream of {@link GrpcMessage}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class GrpcReadStreamBase<S extends GrpcReadStreamBase<S, T>, T> implements GrpcReadStream<T> {

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
  private final WireFormat format;
  private Handler<Throwable> exceptionHandler;
  private Handler<GrpcMessage> messageHandler;
  private Handler<Void> endHandler;
  private Handler<InvalidMessageException> invalidMessageHandler;
  private GrpcMessage last;
  private final GrpcMessageDecoder<T> messageDecoder;
  private final Promise<Void> end;
  private Handler<GrpcError> errorHandler;

  protected GrpcReadStreamBase(Context context,
                               String encoding,
                               WireFormat format,
                               GrpcMessageDecoder<T> messageDecoder) {
    ContextInternal ctx = (ContextInternal) context;
    this.context = ctx;
    this.encoding = encoding;
    this.format = format;
    this.messageDecoder = messageDecoder;
    this.end = ctx.promise();
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

  public abstract S pause();

  public final S resume() {
    return fetch(Long.MAX_VALUE);
  }

  public abstract S fetch(long amount);

  @Override
  public final S exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return (S) this;
  }

  @Override
  public final S errorHandler(@Nullable Handler<GrpcError> handler) {
    errorHandler = handler;
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
  public abstract S handler(@Nullable Handler<T> handler);

  @Override
  public final S endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return (S) this;
  }

  public final void tryFail(Throwable err) {
    if (end.tryFail(err)) {
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        context.dispatch(err, handler);
      }
    }
  }

  public final void handleException(Throwable err) {
    if (err instanceof InvalidMessageException) {
      InvalidMessageException ime = (InvalidMessageException) err;
      handleInvalidMessage(ime);
    } else if (err instanceof GrpcErrorException) {
      Handler<GrpcError> handler = errorHandler;
      if (handler != null) {
        handler.handle(((GrpcErrorException)err).error());
      }
    } else {
      tryFail(err);
    }
  }

  protected void handleEnd() {
    end.tryComplete();
    Handler<Void> handler = endHandler;
    if (handler != null) {
      context.dispatch(handler);
    }
  }

  public void handleInvalidMessage(InvalidMessageException e) {
    Handler<InvalidMessageException> handler = invalidMessageHandler;
    if (handler != null) {
      context.dispatch(e, handler);
    }
  }

  public void handleMessage(GrpcMessage msg) {
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
