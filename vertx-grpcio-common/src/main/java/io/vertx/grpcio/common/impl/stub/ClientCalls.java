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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author Rogelio Orts
 * @author Eduard Català
 */
public final class ClientCalls {

  private ClientCalls() {
  }

  public static <I, O> Future<O> oneToOne(ContextInternal ctx, I request, BiConsumer<I, StreamObserver<O>> delegate) {
    Promise<O> promise = ctx != null ? ctx.promise() : Promise.promise();
    delegate.accept(request, toStreamObserver(promise));
    return promise.future();
  }

  public static <I, O> Future<ReadStream<O>> oneToMany(ContextInternal ctx, I request, BiConsumer<I, StreamObserver<O>> delegate) {
    return oneToMany(ctx, request, delegate, null, null, null);
  }

  public static <I, O> Future<ReadStream<O>> oneToMany(ContextInternal ctx, I request, BiConsumer<I, StreamObserver<O>> delegate, Handler<O> handler, Handler<Void> endHandler, Handler<Throwable> exceptionHandler) {
    StreamObserverReadStream<O> response = new StreamObserverReadStream<>();
    response.handler(handler).endHandler(endHandler).exceptionHandler(exceptionHandler);
    delegate.accept(request, response);
    return Future.succeededFuture(response);
  }

  public static <I, O> Future<O> manyToOne(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate) {
    Promise<O> promise = ctx != null ? ctx.promise() : Promise.promise();
    StreamObserver<I> request = delegate.apply(toStreamObserver(promise));
    requestHandler.succeed(new GrpcWriteStream<>(request));
    return promise.future();
  }

  public static <I, O> Future<ReadStream<O>> manyToMany(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate) {
    return manyToMany(ctx, requestHandler, delegate, null);
  }

  public static <I, O> Future<ReadStream<O>> manyToMany(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate, Handler<Throwable> exceptionHandler) {
    return manyToMany(ctx, requestHandler, delegate, null, null, null);
  }

  public static <I, O> Future<ReadStream<O>> manyToMany(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate, Handler<O> handler, Handler<Void> endHandler, Handler<Throwable> exceptionHandler) {
    StreamObserverReadStream<O> response = new StreamObserverReadStream<>();
    response.handler(handler).endHandler(endHandler).exceptionHandler(exceptionHandler);
    StreamObserver<I> request = delegate.apply(response);
    requestHandler.complete(new GrpcWriteStream<>(request), null);
    return Future.succeededFuture(response);
  }

  private static <O> StreamObserver<O> toStreamObserver(Promise<O> promise) {
    return new StreamObserver<O>() {
      @Override
      public void onNext(O tResponse) {
        if (!promise.tryComplete(tResponse)) {
          throw Status.INTERNAL
            .withDescription("More than one responses received for unary or client-streaming call")
            .asRuntimeException();
        }
      }

      @Override
      public void onError(Throwable throwable) {
        promise.tryFail(throwable);
      }

      @Override
      public void onCompleted() {
        // Do nothing
      }
    };
  }
}
