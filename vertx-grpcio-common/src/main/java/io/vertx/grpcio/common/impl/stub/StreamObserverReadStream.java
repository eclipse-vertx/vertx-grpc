/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.grpcio.common.impl.stub;

import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.streams.ReadStream;

import java.util.Objects;

/**
 * @author Rogelio Orts
 */
public class StreamObserverReadStream<T> implements StreamObserver<T>, ReadStream<T> {

  private static final EndOfStream END_SENTINEL = new EndOfStream(null);

  private static class EndOfStream {
    final Throwable failure;
    EndOfStream(Throwable failure) {
      this.failure = failure;
    }
  }

  private final CallStreamObserver<?> streamObserver;
  private final InboundMessageQueue<Object> queue;
  private Handler<Throwable> exceptionHandler;
  private Handler<T> handler;
  private Handler<Void> endHandler;
  private boolean paused;

  public StreamObserverReadStream(ContextInternal context, CallStreamObserver<?> streamObserver) {
    this.streamObserver = streamObserver;
    this.paused = false;
    this.queue = new InboundMessageQueue<>(context.executor(), context.executor()) {
      @Override
      protected void handleMessage(Object msg) {
        Handler h;
        if (msg instanceof EndOfStream) {
          Throwable failure = ((EndOfStream) msg).failure;
          if (failure != null) {
            h = exceptionHandler;
            msg = failure;
          } else {
            h = endHandler;
            msg = null;
          }
        } else {
          h = handler;
        }
        if (h != null) {
          h.handle(msg);
        }
      }
      @Override
      protected void handleResume() {
        paused = false;
        streamObserver.request(1);
      }
      @Override
      protected void handlePause() {
        paused = true;
      }
    };
  }

  public void init() {
    streamObserver.disableAutoInboundFlowControl();
    streamObserver.request(1);
  }

  @Override
  public void onNext(T t) {
    queue.write(t);
    if (!paused) {
      streamObserver.request(1);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    queue.write(new EndOfStream(throwable));
  }

  @Override
  public void onCompleted() {
    queue.write(END_SENTINEL);
  }

  @Override
  public synchronized ReadStream<T> exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized ReadStream<T> handler(@Nullable Handler<T> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public synchronized ReadStream<T> endHandler(@Nullable Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public ReadStream<T> pause() {
    queue.pause();
    return this;
  }

  @Override
  public ReadStream<T> resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public ReadStream<T> fetch(long amount) {
    queue.fetch(amount);
    return this;
  }
}
