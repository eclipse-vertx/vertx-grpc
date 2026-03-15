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
import io.vertx.core.http.HttpClientRequest;

import java.time.Duration;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.DefaultGrpcHeadersFrame;
import io.vertx.grpc.common.impl.DefaultGrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;
import io.vertx.grpc.common.impl.GrpcWriteStreamBase;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcClientRequestImpl<Req, Resp> extends GrpcWriteStreamBase<GrpcClientRequestImpl<Req, Resp>, Req> implements GrpcClientRequest<Req, Resp> {

  private final GrpcClientInvokerResolver invokerResolver;
  private final boolean scheduleDeadline;
  private final GrpcMessageDecoder<Resp> messageDecoder;
  private Http2GrpcInboundInvoker invoker;
  private ServiceName serviceName;
  private String methodName;
  private Promise<GrpcClientResponse<Req, Resp>> response;
  private long timeout;
  private TimeUnit timeoutUnit;
  private Timer deadline;

  private GrpcClientResponseImpl<Req, Resp> r;
  private ReadStream<GrpcMessage> r2;
  private Handler<GrpcMessage> messageHandler;

  public GrpcClientRequestImpl(HttpClientRequest httpRequest,
                               long maxMessageSize,
                               boolean scheduleDeadline,
                               GrpcMessageEncoder<Req> messageEncoder,
                               GrpcMessageDecoder<Resp> messageDecoder) {
    super( ((PromiseInternal<?>)httpRequest.response()).context(), "application/grpc", httpRequest, messageEncoder);

    Promise<GrpcClientResponse<Req, Resp>> promise = context().promise();

    this.scheduleDeadline = scheduleDeadline;
    this.timeout = 0L;
    this.timeoutUnit = null;
    this.invokerResolver = new Http2GrpcClientInvokerResolver(httpRequest, maxMessageSize);
    this.response = promise;
    this.messageDecoder = messageDecoder;
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
    invoker.write(new SetIdleTimeoutFrame(Duration.ofMillis(timeout)));
    return this;
  }

  @Override
  protected Future<Void> sendHeaders(String contentType, String encoding, MultiMap headers) {
    return sendHeaders(contentType, encoding, headers, false);
  }

  private Future<Void> sendHeaders(String contentType, String encoding, MultiMap headers, boolean end) {

    ServiceName serviceName = this.serviceName;
    String methodName = this.methodName;
    if (serviceName == null) {
      throw new IllegalStateException();
    }
    if (methodName == null) {
      throw new IllegalStateException();
    }

    invoker = invokerResolver.resolveInvoker(serviceName, methodName);

    invoker.handler(frame -> {
      if (frame instanceof GrpcHeadersFrame) {

        r2 = new GrpcClientRequestImpl.StreamBase() {
          @Override
          public ReadStream<GrpcMessage> exceptionHandler(@Nullable Handler<Throwable> handler) {
            invoker.exceptionHandler(handler);
            return this;
          }
          @Override
          public ReadStream<GrpcMessage> handler(@Nullable Handler<GrpcMessage> handler) {
            messageHandler = handler;
            return this;
          }
          @Override
          public ReadStream<GrpcMessage> endHandler(@Nullable Handler<Void> handler) {
            invoker.endHandler(handler);
            return this;
          }
          @Override
          public ReadStream<GrpcMessage> pause() {
            invoker.pause();
            return this;
          }
          @Override
          public ReadStream<GrpcMessage> resume() {
            invoker.resume();
            return this;
          }
          @Override
          public ReadStream<GrpcMessage> fetch(long amount) {
            invoker.fetch(amount);
            return this;
          }
        };

        GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;

        // Todo : move this to GrpcHeadersFrame
        WireFormat format;
        if (headersFrame.contentType() != null) {
          format = GrpcMediaType.parseContentType(headersFrame.contentType(), "application/grpc");
        } else {
          format = null;
        }

        r = new GrpcClientResponseImpl<>(context(), GrpcClientRequestImpl.this,
          r2, format, headersFrame.encoding(), messageDecoder);

        r.invalidMessageHandler(invalidMsg -> {
          cancel();
          r.tryFail(invalidMsg);
        });

        r.init(GrpcClientRequestImpl.this);

        r.handleHeaders(headersFrame.headers());

        response.tryComplete(r);

      } else if (frame instanceof GrpcMessageFrame) {
        GrpcMessageFrame messageFrame = (GrpcMessageFrame)frame;
        Handler<GrpcMessage> handler = messageHandler;
        if (handler != null) {
          handler.handle(messageFrame.message());
        }
      } else if (frame instanceof GrpcTrailersFrame) {
        GrpcTrailersFrame trailersFrame = (GrpcTrailersFrame) frame;
        if (r == null) {
          r = new GrpcClientResponseImpl<>(context(), GrpcClientRequestImpl.this,
            new GrpcClientRequestImpl.StreamBase() {
              @Override
              public ReadStream<GrpcMessage> endHandler(@Nullable Handler<Void> handler) {
                invoker.endHandler(handler);
                return this;
              }
            }, WireFormat.PROTOBUF, null, messageDecoder);
          r.init(GrpcClientRequestImpl.this);
          r.handleHeaders(trailersFrame.trailers());
          r.handleTrailers(trailersFrame.status(), trailersFrame.statusMessage(), HttpHeaders.headers());
          response.tryComplete(r);
        } else {
          r.handleTrailers(trailersFrame.status(), trailersFrame.statusMessage(), trailersFrame.trailers());
        }
      } else {
        System.out.println("handle me " + frame);
      }
    });

    invoker.exceptionHandler(err -> {
      response.tryFail(err);
    });

    invoker.init();

    Duration to = timeout > 0L ? Duration.of(timeout, timeoutUnit.toChronoUnit()) : null;
    if (scheduleDeadline && timeout > 0L) {
      Timer timer = context.timer(timeout, timeoutUnit);
      deadline = timer;
      timer.onSuccess(v -> {
        cancel();
      });
    }

    GrpcHeadersFrame frame = new DefaultGrpcHeadersFrame(contentType, encoding, headers, to);

    if (end) {
      return invoker.end(frame);
    } else {
      return invoker.write(frame);
    }
  }

  @Override
  protected Future<Void> sendTrailers(String contentType, String encoding, MultiMap headers, MultiMap trailers) {
    return sendHeaders(contentType, encoding, headers, true);
  }

  @Override
  protected Future<Void> sendTrailers(MultiMap trailers) {
    return invoker.end();
  }

  @Override
  protected Future<Void> sendMessage(GrpcMessage message) {
    return invoker.write(new DefaultGrpcMessageFrame(message));
  }

  void cancelTimeout() {
    Timer timer = deadline;
    if (timer != null && timer.cancel()) {
      deadline = null;
    }
  }

  @Override public Future<GrpcClientResponse<Req, Resp>> response() {
    return response.future();
  }

  @Override
  protected boolean sendCancel() {
    invoker
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

  private static class StreamBase implements ReadStream<GrpcMessage> {
    @Override
    public ReadStream<GrpcMessage> exceptionHandler(@Nullable Handler<Throwable> handler) {
      return this;
    }
    @Override
    public ReadStream<GrpcMessage> handler(@Nullable Handler<GrpcMessage> handler) {
      return this;
    }
    @Override
    public ReadStream<GrpcMessage> pause() {
      return this;
    }
    @Override
    public ReadStream<GrpcMessage> resume() {
      return this;
    }
    @Override
    public ReadStream<GrpcMessage> fetch(long amount) {
      return this;
    }
    @Override
    public ReadStream<GrpcMessage> endHandler(@Nullable Handler<Void> endHandler) {
      return this;
    }
  }
}
