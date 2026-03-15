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

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;

import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcInboundInvoker;
import io.vertx.grpc.common.impl.GrpcReadStreamBase;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcClientResponseImpl<Req, Resp> extends GrpcReadStreamBase<GrpcClientResponseImpl<Req, Resp>, Resp> implements GrpcClientResponse<Req, Resp> {

  private final GrpcInboundInvoker invoker;
  private final GrpcClientRequestImpl<Req, Resp> request;
  private GrpcStatus status;
  private MultiMap headers;
  private MultiMap trailers;
  private String statusMessage;

  public GrpcClientResponseImpl(ContextInternal context,
                                GrpcClientRequestImpl<Req, Resp> request,
                                GrpcInboundInvoker invoker,
                                WireFormat format,
                                String encoding,
                                GrpcMessageDecoder<Resp> messageDecoder) {
    super(
      context,
      encoding,
      format,
      messageDecoder);
    this.request = request;
    this.invoker = invoker;
  }

  void handleHeaders(MultiMap headers) {
    this.headers = headers;
  }

  void handleTrailers(GrpcStatus status,  String statusMessage, MultiMap trailers) {
    this.status = status;
    this.statusMessage = statusMessage;
    this.trailers = trailers;
  }

  @Override
  public GrpcClientResponseImpl<Req, Resp> pause() {
    invoker.pause();
    return this;
  }

  @Override
  public GrpcClientResponseImpl<Req, Resp> fetch(long amount) {
    invoker.fetch(amount);
    return this;
  }

  @Override
  public GrpcClientRequest<Req, Resp> request() {
    return request;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public MultiMap trailers() {
    return trailers;
  }

  public void handleEnd() {
    request.cancelTimeout();
    super.handleEnd();
    request.handleStatus(status);
    if (!request.isTrailersSent()) {
      request.cancel();
    }
  }

  @Override
  public GrpcStatus status() {
    return status;
  }

  @Override
  public String statusMessage() {
    return statusMessage;
  }

  @Override
  public Future<Void> end() {
    return super.end()
      .expecting(new Expectation<>() {
        @Override
        public boolean test(Void value) {
          return status() == GrpcStatus.OK;
        }
        @Override
        public Throwable describe(Void value) {
          return new InvalidStatusException(GrpcStatus.OK, status());
        }
      });
  }

  @Override
  public GrpcClientResponseImpl<Req, Resp> handler(Handler<Resp> handler) {
    if (handler != null) {
      return messageHandler(msg -> {
        Resp decoded;
        try {
          decoded = decodeMessage(msg);
        } catch (CodecException e) {
          request.cancel();
          return;
        }
        handler.handle(decoded);
      });
    } else {
      return messageHandler(null);
    }
  }
}
