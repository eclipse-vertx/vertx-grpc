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
package io.vertx.grpc.client.impl;

import io.grpc.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcMessageImpl;
import io.vertx.grpc.common.impl.VertxScheduledExecutorService;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcClientRequestImpl<Req, Resp> implements GrpcClientRequest<Req, Resp> {

  private final ContextInternal context;
  private final HttpClientRequest httpRequest;
  private final GrpcMessageEncoder<Req> messageEncoder;
  private ServiceName serviceName;
  private String methodName;
  private String encoding = null;
  private boolean headersSent;
  private boolean cancelled;
  boolean trailersSent;
  private Future<GrpcClientResponse<Req, Resp>> response;
  private MultiMap headers;
  private long timeout;
  private TimeUnit timeoutUnit;
  private io.grpc.Context grpcContext;

  public GrpcClientRequestImpl(HttpClientRequest httpRequest, GrpcMessageEncoder<Req> messageEncoder, GrpcMessageDecoder<Resp> messageDecoder) {
    this.context = ((PromiseInternal<?>)httpRequest.response()).context();
    this.httpRequest = httpRequest;
    this.messageEncoder = messageEncoder;
    this.response = httpRequest
      .response()
      .map(httpResponse -> {
        GrpcClientResponseImpl<Req, Resp> grpcResponse = new GrpcClientResponseImpl<>(context, grpcContext, this, httpResponse, messageDecoder);
        grpcResponse.init();
        return grpcResponse;
      });
  }

  @Override
  public MultiMap headers() {
    if (headersSent) {
      throw new IllegalStateException("Headers already sent");
    }
    if (headers == null) {
      headers = MultiMap.caseInsensitiveMultiMap();
    }
    return headers;
  }

  @Override
  public GrpcClientRequest<Req, Resp> serviceName(ServiceName serviceName) {
    this.serviceName = serviceName;
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> fullMethodName(String fullMethodName) {
    if (headersSent) {
      throw new IllegalStateException("Request already sent");
    }
    int idx = fullMethodName.lastIndexOf('/');
    if (idx == -1) {
      throw new IllegalArgumentException();
    }
    this.serviceName = ServiceName.create(fullMethodName.substring(0, idx));
    this.methodName = fullMethodName.substring(idx + 1);
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> methodName(String methodName) {
    this.methodName = methodName;
    return this;
  }

  @Override public GrpcClientRequest<Req, Resp> encoding(String encoding) {
    Objects.requireNonNull(encoding);
    this.encoding = encoding;
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> exceptionHandler(Handler<Throwable> handler) {
    httpRequest.exceptionHandler(handler);
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> setWriteQueueMaxSize(int maxSize) {
    httpRequest.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return httpRequest.writeQueueFull();
  }

  @Override
  public GrpcClientRequest<Req, Resp> drainHandler(Handler<Void> handler) {
    httpRequest.drainHandler(handler);
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> timeout(long timeout, TimeUnit unit) {
    if (timeout < 0L) {
      throw new IllegalArgumentException("Timeout must be positive");
    }
    if (headersSent) {
      throw new IllegalStateException("Timeout must be set before sending request headers");
    }
    this.timeoutUnit = Objects.requireNonNull(unit);
    this.timeout = timeout;
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> idleTimeout(long timeout) {
    httpRequest.idleTimeout(timeout);
    return this;
  }

  @Override public Future<Void> writeMessage(GrpcMessage message) {
    return writeMessage(message, false);
  }

  @Override public Future<Void> endMessage(GrpcMessage message) {
    return writeMessage(message, true);
  }

  @Override public Future<Void> end() {
    if (cancelled) {
      throw new IllegalStateException("The stream has been cancelled");
    }
    if (!headersSent) {
      throw new IllegalStateException("You must send a message before terminating the stream");
    }
    if (trailersSent) {
      throw new IllegalStateException("The stream has been closed");
    }
    trailersSent = true;
    return httpRequest.end();
  }

  private Future<Void> writeMessage(GrpcMessage message, boolean end) {
    if (cancelled) {
      throw new IllegalStateException("The stream has been cancelled");
    }
    if (trailersSent) {
      throw new IllegalStateException("The stream has been closed");
    }
    if (encoding != null && !encoding.equals(message.encoding())) {
      switch (encoding) {
        case "gzip":
          message = GrpcMessageEncoder.GZIP.encode(message.payload());
          break;
        case "identity":
          if (!message.encoding().equals("identity")) {
            if (!message.encoding().equals("gzip")) {
              return Future.failedFuture("Encoding " + message.encoding() + " is not supported");
            }
            Buffer decoded;
            try {
              decoded = GrpcMessageDecoder.GZIP.decode(message);
            } catch (CodecException e) {
              return Future.failedFuture(e);
            }
            message = GrpcMessage.message("identity", decoded);
          }
          break;
      }
    }

    if (!headersSent) {
      ServiceName serviceName = this.serviceName;
      String methodName = this.methodName;
      if (serviceName == null) {
        throw new IllegalStateException();
      }
      if (methodName == null) {
        throw new IllegalStateException();
      }
      if (headers != null) {
        MultiMap requestHeaders = httpRequest.headers();
        for (Map.Entry<String, String> header : headers) {
          requestHeaders.add(header.getKey(), header.getValue());
        }
      }
      if (timeout > 0L) {
        httpRequest.putHeader("grpc-timeout", timeoutUnit.toMicros(timeout) + "u");
      }
      String uri = serviceName.pathOf(methodName);
      httpRequest.putHeader("content-type", "application/grpc");
      if (encoding != null) {
        httpRequest.putHeader("grpc-encoding", encoding);
      }
      httpRequest.putHeader("grpc-accept-encoding", "gzip");
      httpRequest.putHeader("te", "trailers");
      httpRequest.setChunked(true);
      httpRequest.setURI(uri);
      headersSent = true;
    }
    if (timeout > 0L) {
      Context.CancellableContext deadlineContext = Context.current().withDeadlineAfter(timeout, timeoutUnit, new VertxScheduledExecutorService(context));
      deadlineContext.addListener(ctx_ -> {
        cancel();
      }, Runnable::run);
      grpcContext = deadlineContext;
    } else {
      grpcContext = Context.current();
    }
    if (end) {
      trailersSent = true;
      return httpRequest.end(GrpcMessageImpl.encode(message));
    } else {
      return httpRequest.write(GrpcMessageImpl.encode(message));
    }
  }

  @Override
  public Future<Void> write(Req message) {
    return writeMessage(messageEncoder.encode(message));
  }

  @Override
  public Future<Void> end(Req message) {
    return endMessage(messageEncoder.encode(message));
  }

  @Override public Future<GrpcClientResponse<Req, Resp>> response() {
    return response;
  }

  @Override
  public void cancel() {
    if (cancelled) {
      return;
    }
    cancelled = true;
    context.execute(() -> {
      boolean responseEnded;
      if (response.failed()) {
        return;
      } else if (response.succeeded()) {
        GrpcClientResponse<Req, Resp> resp = response.result();
        if (resp.end().failed()) {
          return;
        } else {
          responseEnded = resp.end().succeeded();
        }
      } else {
        responseEnded = false;
      }
      if (!trailersSent || !responseEnded) {
        httpRequest.reset(GrpcError.CANCELLED.http2ResetCode);
      }
    });
  }

  @Override
  public HttpConnection connection() {
    return httpRequest.connection();
  }
}
