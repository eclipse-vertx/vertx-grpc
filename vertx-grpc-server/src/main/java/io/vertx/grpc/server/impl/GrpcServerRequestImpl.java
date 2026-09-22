/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.server.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Timer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcInboundStream;
import io.vertx.grpc.common.impl.GrpcReadStreamBase;
import io.vertx.grpc.common.impl.GrpcValidationStream;
import io.vertx.grpc.server.GrpcServerRequest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerRequestImpl<Req, Resp> implements GrpcServerRequest<Req, Resp> {

  private final ContextInternal context;
  private final ServiceName serviceName;
  private final String fullMethodName;
  private final String methodName;
  private final GrpcInboundStream inbound;
  private final MultiMap headers;
  private final Stream base;
  private final GrpcReadStream<Req> stream;
  final Duration timeout;
  private GrpcServerResponseImpl<Req, Resp> response;
  private Timer deadline;

  public GrpcServerRequestImpl(ContextInternal context,
                               MultiMap headers,
                               WireFormat format,
                               GrpcInboundStream inbound,
                               Duration timeout,
                               String encoding,
                               GrpcMessageDecoder<Req> messageDecoder,
                               GrpcMessageValidator<? super Req> messageValidator,
                               ServiceName serviceName,
                               String fullMethodName,
                               String methodName) {
    this.context = context;
    this.inbound = inbound;
    this.headers = headers;
    this.timeout = timeout;
    this.serviceName = serviceName;
    this.fullMethodName = fullMethodName;
    this.methodName = methodName;
    this.base = new Stream(context, encoding, format, messageDecoder);
    this.stream = messageValidator == null ? base : new GrpcValidationStream<>(base, messageValidator);
  }

  ContextInternal context() {
    return context;
  }

  public void init(GrpcServerResponseImpl<Req, Resp> ws, boolean scheduleDeadline) {
    this.response = ws;
    if (timeout != null && (!timeout.isNegative() && !timeout.isZero())) {
      if (scheduleDeadline) {
        Timer timer = context.timer(timeout.toMillis(), TimeUnit.MILLISECONDS);
        deadline = timer;
        timer.onSuccess(v -> {
          response.handleTimeout();
        });
      }
    }
  }

  void cancelTimeout() {
    Timer timer = deadline;
    if (timer != null) {
      deadline = null;
      timer.cancel();
    }
  }

  public void handleMessage(GrpcMessage msg) {
    base.handleMessage(msg);
  }

  public void handleEnd() {
    base.handleEnd();
  }

  public void handleException(Throwable err) {
    base.handleException(err);
    response.handleException(err);
  }

  public void handleError(GrpcError error) {
    base.handleError(error);
    response.handleError(error);
  }

  public String fullMethodName() {
    return fullMethodName;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public ServiceName serviceName() {
    return serviceName;
  }

  @Override
  public String methodName() {
    return methodName;
  }

  @Override
  public String encoding() {
    return stream.encoding();
  }

  @Override
  public WireFormat format() {
    return stream.format();
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> handler(@Nullable Handler<Req> handler) {
    stream.handler(handler);
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> messageHandler(@Nullable Handler<GrpcMessage> handler) {
    stream.messageHandler(handler);
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> invalidMessageHandler(@Nullable Handler<InvalidMessageException> handler) {
    stream.invalidMessageHandler(handler);
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> errorHandler(@Nullable Handler<GrpcError> handler) {
    stream.errorHandler(handler);
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> exceptionHandler(@Nullable Handler<Throwable> handler) {
    stream.exceptionHandler(handler);
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> endHandler(@Nullable Handler<Void> handler) {
    stream.endHandler(handler);
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> pause() {
    stream.pause();
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> resume() {
    stream.resume();
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> fetch(long amount) {
    stream.fetch(amount);
    return this;
  }

  @Override
  public Future<Req> last() {
    return stream.last();
  }

  @Override
  public Future<Void> end() {
    return stream.end();
  }

  public GrpcServerResponseImpl<Req, Resp> response() {
    return response;
  }

  @Override
  public HttpConnection connection() {
    return null;
  }

  @Override
  public long timeout() {
    return timeout == null ? 0L : timeout.toMillis();
  }

  @Override
  public Timer deadline() {
    return deadline;
  }

  private class Stream extends GrpcReadStreamBase<Stream, Req> {

    Stream(ContextInternal context, String encoding, WireFormat format, GrpcMessageDecoder<Req> messageDecoder) {
      super(context, encoding, format, messageDecoder);
    }

    @Override
    public MultiMap headers() {
      return headers;
    }

    @Override
    public Stream pause() {
      inbound.pause();
      return this;
    }

    @Override
    public Stream fetch(long amount) {
      inbound.fetch(amount);
      return this;
    }

    @Override
    public Stream handler(@Nullable Handler<Req> handler) {
      if (handler != null) {
        return messageHandler(msg -> {
          Req decoded;
          try {
            decoded = decodeMessage(msg);
          } catch (CodecException e) {
            response.cancel();
            return;
          }
          try {
            handler.handle(decoded);
          } catch (Exception e) {
            response.fail(e);
          }
        });
      } else {
        return messageHandler(null);
      }
    }
  }
}
