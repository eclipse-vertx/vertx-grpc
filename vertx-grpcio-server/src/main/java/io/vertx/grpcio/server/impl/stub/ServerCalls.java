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
package io.vertx.grpcio.server.impl.stub;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Completable;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.server.StatusException;
import io.vertx.grpcio.common.impl.stub.GrpcWriteStream;
import io.vertx.grpcio.common.impl.stub.StreamObserverReadStream;

import java.util.function.BiConsumer;

/**
 * @author Rogelio Orts
 * @author Eduard Català
 */
public final class ServerCalls {

  private ServerCalls() {
  }

  public static <I, O> void oneToOne(ContextInternal ctx, I request, StreamObserver<O> response, String compression, BiConsumer<I, Completable<O>> delegate) {
    trySetCompression(response, compression);
    try {
      delegate.accept(request, (res, err) -> {
        if (err == null) {
          response.onNext(res);
          response.onCompleted();
        } else {
          response.onError(prepareError(err));
        }
      });
    } catch (Throwable throwable) {
      response.onError(prepareError(throwable));
    }
  }

  public static <I, O> void oneToMany(ContextInternal ctx, I request, StreamObserver<O> response, String compression, BiConsumer<I, WriteStream<O>> delegate) {
    trySetCompression(response, compression);
    try {
      GrpcWriteStream<O> responseWriteStream = new GrpcWriteStream<>(ctx, response);
      delegate.accept(request, responseWriteStream);
    } catch (Throwable throwable) {
      response.onError(prepareError(throwable));
    }
  }

  public static <I, O> StreamObserver<I> manyToOne(ContextInternal ctx, StreamObserver<O> response, String compression, BiConsumer<ReadStream<I>, Completable<O>> delegate) {
    trySetCompression(response, compression);
    StreamObserverReadStream<I> request = new StreamObserverReadStream<>(ctx, (CallStreamObserver<?>) response);
    request.init();
    Completable<O> completable = (res,err) -> {
      if (err == null) {
        response.onNext(res);
        response.onCompleted();
      } else {
        response.onError(prepareError(err));
      }
    };
    try {
      delegate.accept(request, completable);
    } catch (Throwable throwable) {
      response.onError(prepareError(throwable));
      return request;
    }

    return request;
  }

  public static <I, O> StreamObserver<I> manyToMany(ContextInternal ctx, StreamObserver<O> response, String compression, BiConsumer<ReadStream<I>, WriteStream<O>> delegate) {
    trySetCompression(response, compression);
    StreamObserverReadStream<I> request = new StreamObserverReadStream<>(ctx, (CallStreamObserver<?>) response);
    request.init();
    GrpcWriteStream<O> responseStream = new GrpcWriteStream<>(ctx, response);
    try {
      delegate.accept(request, responseStream);
    } catch (Throwable throwable) {
      response.onError(prepareError(throwable));
    }
    return request;
  }

  private static void trySetCompression(StreamObserver<?> response, String compression) {
    if (compression != null && response instanceof ServerCallStreamObserver<?>) {
      ServerCallStreamObserver<?> serverResponse = (ServerCallStreamObserver<?>) response;
      serverResponse.setCompression(compression);
    }
  }

  private static Throwable prepareError(Throwable throwable) {
    if (throwable instanceof StatusException) {
      return new StatusRuntimeException(Status.fromCode(Status.Code.valueOf(((StatusException)throwable).status().name())));
    } else if (throwable instanceof UnsupportedOperationException) {
      return new StatusRuntimeException(Status.UNIMPLEMENTED);
    } else if (throwable instanceof io.grpc.StatusException || throwable instanceof StatusRuntimeException) {
      return throwable;
    } else {
      return Status.fromThrowable(throwable).asException();
    }
  }
}
