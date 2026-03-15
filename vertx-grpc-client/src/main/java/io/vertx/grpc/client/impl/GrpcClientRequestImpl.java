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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Timer;

import java.time.Duration;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.DefaultGrpcCancelFrame;
import io.vertx.grpc.common.impl.DefaultGrpcHeadersFrame;
import io.vertx.grpc.common.impl.DefaultGrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;
import io.vertx.grpc.common.impl.GrpcWriteStreamBase;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcClientRequestImpl<Req, Resp> extends GrpcWriteStreamBase<GrpcClientRequestImpl<Req, Resp>, Req> implements GrpcClientRequest<Req, Resp> {

  private final GrpcClientInvoker invoker;
  private final boolean scheduleDeadline;
  private final GrpcMessageDecoder<Resp> messageDecoder;
  private GrpcStream stream;
  private ServiceName serviceName;
  private String methodName;
  private Promise<GrpcClientResponse<Req, Resp>> responsePromise;
  private long timeout;
  private TimeUnit timeoutUnit;
  private Timer deadline;
  private GrpcClientResponseImpl<Req, Resp> response;
  private Handler<Void> drainHandler;

  public GrpcClientRequestImpl(ContextInternal context,
                               GrpcClientInvoker invoker,
                               boolean scheduleDeadline,
                               GrpcMessageEncoder<Req> messageEncoder,
                               GrpcMessageDecoder<Resp> messageDecoder) {
    super(context, messageEncoder);

    Promise<GrpcClientResponse<Req, Resp>> promise = context().promise();

    this.invoker = invoker;
    this.scheduleDeadline = scheduleDeadline;
    this.timeout = 0L;
    this.timeoutUnit = null;
    this.responsePromise = promise;
    this.messageDecoder = messageDecoder;
  }

  @Override
  public GrpcClientRequest<Req, Resp> setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> drainHandler(@Nullable Handler<Void> handler) {
    GrpcStream s = stream;
    if (s != null) {
      s.drainHandler(handler);
    } else {
      drainHandler = handler;
    }
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    GrpcStream s = stream;
    return s != null && s.writeQueueFull();
  }

  @Override
  public GrpcClientRequest<Req, Resp> serviceName(ServiceName serviceName) {
    this.serviceName = serviceName;
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> fullMethodName(String fullMethodName) {
    if (isHeadersSent()) {
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

  @Override
  public GrpcClientRequest<Req, Resp> timeout(long timeout, TimeUnit unit) {
    if (timeout < 0L) {
      throw new IllegalArgumentException("Timeout must be positive");
    }
    if (isHeadersSent()) {
      throw new IllegalStateException("Timeout must be set before sending request headers");
    }
    String headerValue = toTimeoutHeader(timeout, unit);
    if (headerValue == null) {
      throw new IllegalArgumentException("Not a valid gRPC timeout value (" + timeout + ',' + unit + ')');
    }
    this.timeout = timeout;
    this.timeoutUnit = unit;
    return this;
  }

  @Override
  public Timer deadline() {
    return deadline;
  }

  @Override
  public GrpcClientRequest<Req, Resp> idleTimeout(long timeout) {
    stream.write(new SetIdleTimeoutFrame(Duration.ofMillis(timeout)));
    return this;
  }

  @Override
  protected Future<Void> sendHeaders(WireFormat format, String encoding, MultiMap headers) {
    return sendHeaders(format, encoding, headers, false);
  }

  private Future<Void> sendHeaders(WireFormat format, String encoding, MultiMap headers, boolean end) {

    ServiceName serviceName = this.serviceName;
    String methodName = this.methodName;
    if (serviceName == null) {
      throw new IllegalStateException();
    }
    if (methodName == null) {
      throw new IllegalStateException();
    }

    stream = invoker.invoke(serviceName, methodName);
    stream.drainHandler(drainHandler);
    stream.handler(this::handleFrame);
    stream.endHandler(this::handleEnd);
    stream.exceptionHandler(this::internalHandleException);

    Duration to = timeout > 0L ? Duration.of(timeout, timeoutUnit.toChronoUnit()) : null;
    if (scheduleDeadline && timeout > 0L) {
      Timer timer = context.timer(timeout, timeoutUnit);
      deadline = timer;
      timer.onSuccess(v -> {
        cancel();
      });
    }

    GrpcHeadersFrame frame = new DefaultGrpcHeadersFrame(format, encoding, headers, to);

    if (end) {
      return stream.end(frame);
    } else {
      return stream.write(frame);
    }
  }

  @Override
  protected Future<Void> sendTrailers(MultiMap trailers) {
    if (stream == null) {
      WireFormat wireFormat = format;
      if (wireFormat == null) {
        wireFormat = WireFormat.PROTOBUF;
        format = WireFormat.PROTOBUF;
      }
      return sendHeaders(wireFormat, encoding, trailers, true);
    } else {
      return stream.end();
    }
  }

  @Override
  protected Future<Void> sendMessage(GrpcMessage message) {
    return stream.write(new DefaultGrpcMessageFrame(message));
  }

  void cancelTimeout() {
    Timer timer = deadline;
    if (timer != null && timer.cancel()) {
      deadline = null;
    }
  }

  @Override public Future<GrpcClientResponse<Req, Resp>> response() {
    return responsePromise.future();
  }

  @Override
  protected boolean sendCancel() {
    stream
      .write(DefaultGrpcCancelFrame.INSTANCE)
      .onSuccess(v -> handleError(GrpcError.CANCELLED));
    return true;
  }

  @Override
  public HttpConnection connection() {
    return null;
  }

  private static final EnumMap<TimeUnit, Character> GRPC_TIMEOUT_UNIT_SUFFIXES = new EnumMap<>(TimeUnit.class);

  static {
    GRPC_TIMEOUT_UNIT_SUFFIXES.put(TimeUnit.NANOSECONDS, 'n');
    GRPC_TIMEOUT_UNIT_SUFFIXES.put(TimeUnit.MICROSECONDS, 'u');
    GRPC_TIMEOUT_UNIT_SUFFIXES.put(TimeUnit.MILLISECONDS, 'm');
    GRPC_TIMEOUT_UNIT_SUFFIXES.put(TimeUnit.SECONDS, 'S');
    GRPC_TIMEOUT_UNIT_SUFFIXES.put(TimeUnit.MINUTES, 'M');
    GRPC_TIMEOUT_UNIT_SUFFIXES.put(TimeUnit.HOURS, 'H');
  }

  private static final TimeUnit[] GRPC_TIMEOUT_UNITS = {
    TimeUnit.NANOSECONDS,
    TimeUnit.MICROSECONDS,
    TimeUnit.MILLISECONDS,
    TimeUnit.SECONDS,
    TimeUnit.MINUTES,
    TimeUnit.HOURS,
  };

  /**
   * Compute timeout header, returns {@code null} when the timeout value is not valid.
   *
   * @param timeout the timeout
   * @param timeoutUnit the timeout unit
   * @return the grpc-timeout header value, e.g. 1M (1 minute)
   */
  public static String toTimeoutHeader(long timeout, TimeUnit timeoutUnit) {
    for (TimeUnit grpcTimeoutUnit : GRPC_TIMEOUT_UNITS) {
      String res = toTimeoutHeader(timeout, timeoutUnit, grpcTimeoutUnit);
      if (res != null) {
        return res;
      }
    }
    return null;
  }

  private static String toTimeoutHeader(long timeout, TimeUnit srcUnit, TimeUnit grpcTimeoutUnit) {
    long v = grpcTimeoutUnit.convert(timeout, srcUnit);
    if (v < 1_000_000_00) {
      return Long.toString(v) + GRPC_TIMEOUT_UNIT_SUFFIXES.get(grpcTimeoutUnit);
    }
    return null;
  }

  private void handleFrame(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        handleHeadersFrame((GrpcHeadersFrame) frame);
        break;
      case MESSAGE:
        handleMessageFrame((GrpcMessageFrame) frame);
        break;
      case TRAILERS:
        handleTrailersFrame((GrpcTrailersFrame) frame);
        break;
      case CANCEL:
        handleCancelFrame((GrpcCancelFrame) frame);
        break;
      default:
        //
        break;
    }
  }

  private void handleHeadersFrame(GrpcHeadersFrame frame) {
    WireFormat format = frame.format();

    response = new GrpcClientResponseImpl<>(context(), GrpcClientRequestImpl.this,
      stream, format, frame.encoding(), messageDecoder);

    response.invalidMessageHandler(invalidMsg -> {
      cancel();
      response.tryFail(invalidMsg);
    });

    response.handleHeaders(frame.headers());

    responsePromise.tryComplete(response);
  }

  private void handleMessageFrame(GrpcMessageFrame frame) {
    GrpcClientResponseImpl<Req, Resp> r = response;
    if (r != null) {
      r.handleMessage(frame.message());
    }
  }

  private void handleTrailersFrame(GrpcTrailersFrame frame) {
    if (response == null) {
      response = new GrpcClientResponseImpl<>(context(), GrpcClientRequestImpl.this, stream, WireFormat.PROTOBUF,
        null, messageDecoder);
      response.handleHeaders(frame.trailers());
      response.handleTrailers(frame.status(), frame.statusMessage(), HttpHeaders.headers());
      responsePromise.tryComplete(response);
    } else {
      response.handleTrailers(frame.status(), frame.statusMessage(), frame.trailers());
    }
  }

  private void handleCancelFrame(GrpcCancelFrame frame) {
    cancel();
  }

  private void handleEnd(Void v) {
    GrpcClientResponseImpl<Req, Resp> r2 = response;
    if (r2 != null) {
      r2.handleEnd();
    }
  }

  private void internalHandleException(Throwable err) {
    handleException(err);
    if (!responsePromise.tryFail(err)) {
      GrpcClientResponseImpl<Req, Resp> resp = response;
      if (resp != null) {
        resp.handleException(err);
      }
    }
  }
}
