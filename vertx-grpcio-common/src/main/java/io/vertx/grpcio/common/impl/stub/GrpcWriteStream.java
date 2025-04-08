/*
 * Copyright 2019 Eclipse.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertx.grpcio.common.impl.stub;

import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.core.streams.WriteStream;

/**
 *
 * @author ecatala
 */
public class GrpcWriteStream<T> implements WriteStream<T> {

  private static final Object END_SENTINEL = new Object();

  private final OutboundMessageQueue<T> queue;
  private Handler<Void> drainHandler;
  private boolean ended;

  public GrpcWriteStream(ContextInternal context, StreamObserver<T> observer) {
    CallStreamObserver<T> streamObserver = (CallStreamObserver<T>) observer;
    this.queue = new OutboundMessageQueue<>(context.executor()) {
      @Override
      public boolean test(T msg) {
        if (msg == END_SENTINEL) {
          streamObserver.onCompleted();
          return true;
        } else {
          boolean ready = streamObserver.isReady();
          if (ready) {
            streamObserver.onNext(msg);
          }
          return ready;
        }
      }
      @Override
      protected void handleDrained() {
        Handler<Void> handler = drainHandler();
        if (handler != null) {
          handler.handle(null);
        }
      }
    };
    streamObserver.setOnReadyHandler(queue::tryDrain);
  }

  @Override
  public WriteStream<T> exceptionHandler(Handler<Throwable> hndlr) {
    return this;
  }

  @Override
  public Future<Void> write(T data) {
    if (ended) {
      throw new IllegalStateException();
    }
    queue.write(data);
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> end() {
    if (ended) {
      throw new IllegalStateException();
    }
    ended = true;
    queue.write((T) END_SENTINEL);
    return Future.succeededFuture();
  }

  @Override
  public WriteStream<T> setWriteQueueMaxSize(int i) {
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return !queue.isWritable();
  }

  @Override
  public synchronized WriteStream<T> drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  private synchronized Handler<Void> drainHandler() {
    return drainHandler;
  }
}
