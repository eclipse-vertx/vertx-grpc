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
package io.vertx.grpc.server.impl;

import io.grpc.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcReadStreamBase;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerRequestImpl<Req, Resp> extends GrpcReadStreamBase<GrpcServerRequestImpl<Req, Resp>, Req> implements GrpcServerRequest<Req, Resp> {

  private static final Pattern TIMEOUT_PATTERN = Pattern.compile("([0-9]{1,8})([HMSmun])");

  private static final Map<String, TimeUnit> TIMEOUT_MAPPING;

  static {
    Map<String, TimeUnit> timeoutMapping = new HashMap<>();
    timeoutMapping.put("H", TimeUnit.HOURS);
    timeoutMapping.put("M", TimeUnit.MINUTES);
    timeoutMapping.put("S", TimeUnit.SECONDS);
    timeoutMapping.put("m", TimeUnit.MILLISECONDS);
    timeoutMapping.put("u", TimeUnit.MICROSECONDS);
    timeoutMapping.put("n", TimeUnit.NANOSECONDS);
    TIMEOUT_MAPPING = timeoutMapping;
  }

  private static long parseTimeout(String timeout) {
    Matcher matcher = TIMEOUT_PATTERN.matcher(timeout);
    if (matcher.matches()) {
      long value = Long.parseLong(matcher.group(1));
      TimeUnit unit = TIMEOUT_MAPPING.get(matcher.group(2));
      return unit.toMillis(value);
    } else {
      return 0L;
    }
  }

  final HttpServerRequest httpRequest;
  final GrpcServerResponseImpl<Req, Resp> response;
  final Context grpcContext;
  final long timeout;
  private GrpcMethodCall methodCall;


  public GrpcServerRequestImpl(io.grpc.Context grpcContext, io.vertx.core.impl.ContextInternal context, HttpServerRequest httpRequest, GrpcMessageDecoder<Req> messageDecoder, GrpcMessageEncoder<Resp> messageEncoder, GrpcMethodCall methodCall) {
    super(context, grpcContext, httpRequest, httpRequest.headers().get("grpc-encoding"), messageDecoder);
    String timeoutHeader = httpRequest.getHeader("grpc-timeout");
    long timeout = timeoutHeader != null ? parseTimeout(timeoutHeader) : 0L;
    GrpcServerResponseImpl<Req, Resp> response = new GrpcServerResponseImpl<>(context, this, httpRequest.response(), messageEncoder);
    this.grpcContext = grpcContext;
    this.timeout = timeout;
    this.httpRequest = httpRequest;
    this.response = response;
    this.methodCall = methodCall;
  }

  public String fullMethodName() {
    return methodCall.fullMethodName();
  }

  @Override
  public MultiMap headers() {
    return httpRequest.headers();
  }

  @Override
  public String encoding() {
    return httpRequest.getHeader("grpc-encoding");
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
  public GrpcServerRequest<Req, Resp> handler(Handler<Req> handler) {
    if (handler != null) {
      return messageHandler(msg -> {
        Req decoded;
        try {
          decoded = decodeMessage(msg);
        } catch (CodecException e) {
          response.cancel();
          return;
        }
        handler.handle(decoded);
      });
    } else {
      return messageHandler(null);
    }
  }

  public GrpcServerResponse<Req, Resp> response() {
    return response;
  }

  @Override
  public HttpConnection connection() {
    return httpRequest.connection();
  }

  @Override
  public long timeout() {
    return timeout;
  }

  @Override
  public long timeoutExpiration() {
    return grpcContext.getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
  }
}
