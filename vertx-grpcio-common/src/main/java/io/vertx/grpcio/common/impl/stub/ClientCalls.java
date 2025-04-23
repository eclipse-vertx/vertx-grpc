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
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Rogelio Orts
 * @author Eduard Català
 */
public final class ClientCalls {

  private ClientCalls() {
  }

  public static <I, O> Future<O> oneToOne(ContextInternal ctx, I request, io.grpc.ClientCall<I, O> call) {
    Promise<O> promise = ctx.promise();
    io.grpc.stub.ClientCalls.asyncUnaryCall(call, request, toStreamObserver(promise, null));
    return promise.future();
  }

  public static <I, O> Future<O> oneToOne(ContextInternal ctx, I request, BiConsumer<I, StreamObserver<O>> delegate) {
    Promise<O> promise = ctx.promise();
    delegate.accept(request, toStreamObserver(promise, null));
    return promise.future();
  }

  public static <I, O> Future<ReadStream<O>> oneToMany(ContextInternal ctx, I request, BiConsumer<I, StreamObserver<O>> delegate) {
    return oneToMany(ctx, request, delegate, null, null, null);
  }

  public static <I, O> void oneToMany(ContextInternal ctx, I request, Completable<ReadStream<O>> completable, BiConsumer<I, StreamObserver<O>> delegate) {
    oneToMany(ctx, request, completable, delegate, null, null, null);
  }

  public static <I, O> Future<ReadStream<O>> oneToMany(ContextInternal ctx, I request, BiConsumer<I, StreamObserver<O>> delegate, Handler<O> handler, Handler<Void> endHandler, Handler<Throwable> exceptionHandler) {
    Promise<ReadStream<O>> promise = Promise.promise();
    oneToMany(ctx, request, promise, delegate, handler, endHandler, exceptionHandler);
    return promise.future();
  }

  public static <I, O> void oneToMany(ContextInternal ctx, I request, Completable<ReadStream<O>> completable, BiConsumer<I, StreamObserver<O>> delegate, Handler<O> handler, Handler<Void> endHandler, Handler<Throwable> exceptionHandler) {
    delegate.accept(request, new ClientResponseObserver<I, O>() {
      StreamObserverReadStream<O> response;
      @Override
      public void beforeStart(ClientCallStreamObserver<I> requestStream) {
        response = new StreamObserverReadStream<>(ctx, requestStream);
        response.init();
        response.handler(handler).endHandler(endHandler).exceptionHandler(exceptionHandler);
        completable.complete(response, null);
      }
      @Override
      public void onNext(O value) {
        response.onNext(value);
      }
      @Override
      public void onError(Throwable t) {
        StreamObserverReadStream<O> resp = response;
        if (resp != null) {
          resp.onError(t);
        } else {
          completable.fail(t);
        }
      }
      @Override
      public void onCompleted() {
        response.onCompleted();
      }
    });
  }

  public static <I, O> Future<O> manyToOne(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate) {
    Promise<O> promise = ctx.promise();
    ClientResponseObserver<I, O> so = ClientCalls.toStreamObserver(promise, blah -> {
      GrpcWriteStream<I> ws = new GrpcWriteStream<>(ctx, blah);
      requestHandler.succeed(ws);
    });
    delegate.apply(so);
    return promise.future();
  }

  public static <I, O> Future<ReadStream<O>> manyToMany(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate) {
    return manyToMany(ctx, requestHandler, delegate, null);
  }

  public static <I, O> Future<ReadStream<O>> manyToMany(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate, Handler<Throwable> exceptionHandler) {
    return manyToMany(ctx, requestHandler, delegate, null, null, null);
  }

  public static <I, O> Future<ReadStream<O>> manyToMany(ContextInternal ctx, Completable<WriteStream<I>> requestHandler, Function<StreamObserver<O>, StreamObserver<I>> delegate, Handler<O> handler, Handler<Void> endHandler, Handler<Throwable> exceptionHandler) {
    Promise<ReadStream<O>> promise = ctx.promise();
    delegate.apply(new ClientResponseObserver<I, O>() {
      @Override
      public void beforeStart(ClientCallStreamObserver<I> requestStream) {
        StreamObserverReadStream<O> response = new StreamObserverReadStream<>(ctx, requestStream);
        response.init();
        response.handler(handler).endHandler(endHandler).exceptionHandler(exceptionHandler);
        promise.complete(response);
        requestHandler.complete(new GrpcWriteStream<>(ctx, requestStream), null);
      }
      @Override
      public void onNext(O value) {
      }
      @Override
      public void onError(Throwable t) {
      }
      @Override
      public void onCompleted() {
      }
    });
    return promise.future();
  }

  private static <I, O> ClientResponseObserver<I, O> toStreamObserver(Promise<O> promise, Consumer<ClientCallStreamObserver<I>> callback) {
    return new ClientResponseObserver<>() {

      @Override
      public void beforeStart(ClientCallStreamObserver<I> requestStream) {
        if (callback != null) {
          callback.accept(requestStream);
        }
      }

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
