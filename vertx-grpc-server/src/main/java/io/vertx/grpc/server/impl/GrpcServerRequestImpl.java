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

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Timer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcInboundInvoker;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.common.impl.GrpcReadStreamBase;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServerRequest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerRequestImpl<Req, Resp> extends GrpcReadStreamBase<GrpcServerRequestImpl<Req, Resp>, Req> implements GrpcServerRequest<Req, Resp> {

  private final GrpcInboundInvoker invoker;
  private final MultiMap headers;
  final Duration timeout;
  final GrpcProtocol protocol;
  private GrpcServerResponseImpl<Req, Resp> response;
  private final GrpcMethodCall methodCall;
  private Timer deadline;

  public GrpcServerRequestImpl(ContextInternal context,
                               MultiMap headers,
                               GrpcProtocol protocol,
                               WireFormat format,
                               GrpcInboundInvoker invoker,
                               Duration timeout,
                               String encoding,
                               GrpcMessageDecoder<Req> messageDecoder,
                               GrpcMethodCall methodCall) {
    super(context, encoding, format, messageDecoder);

    this.invoker = invoker;
    this.headers = headers;
    this.protocol = protocol;
    this.timeout = timeout;
    this.methodCall = methodCall;
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

  @Override
  public GrpcServerRequestImpl<Req, Resp> pause() {
    invoker.pause();
    return this;
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> fetch(long amount) {
    invoker.fetch(amount);
    return this;
  }

  public String fullMethodName() {
    return methodCall.fullMethodName();
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public ServiceName serviceName() {
    return methodCall.serviceName();
  }

  @Override
  public String methodName() {
    return methodCall.methodName();
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> handler(Handler<Req> handler) {
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

  public GrpcServerResponseImpl<Req, Resp> response() {
    return response;
  }

  @Override
  public HttpConnection connection() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long timeout() {
    return timeout == null ? 0L : timeout.toMillis();
  }

  @Override
  public Timer deadline() {
    return deadline;
  }
}
