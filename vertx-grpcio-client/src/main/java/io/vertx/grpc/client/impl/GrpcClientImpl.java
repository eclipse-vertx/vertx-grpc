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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.impl.GrpcRequestLocal;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class GrpcClientImpl implements GrpcClient {

  private final Vertx vertx;
  private HttpClient client;
  private boolean closeClient;
  private final boolean scheduleDeadlineAutomatically;
  private final int timeout;
  private final TimeUnit timeoutUnit;

  public GrpcClientImpl(Vertx vertx, GrpcClientOptions grpcOptions, HttpClientOptions httpOptions) {
    this(vertx, grpcOptions, vertx.createHttpClient(new HttpClientOptions(httpOptions).setProtocolVersion(HttpVersion.HTTP_2)), true);
  }

  public GrpcClientImpl(Vertx vertx) {
    this(vertx, new GrpcClientOptions(), new HttpClientOptions().setHttp2ClearTextUpgrade(false));
  }

  public GrpcClientImpl(Vertx vertx, HttpClient client) {
    this(vertx, new GrpcClientOptions(), client, false);
  }

  private GrpcClientImpl(Vertx vertx, GrpcClientOptions grpcOptions, HttpClient client, boolean close) {
    this.vertx = vertx;
    this.client = client;
    this.scheduleDeadlineAutomatically = grpcOptions.getScheduleDeadlineAutomatically();
    this.timeout = grpcOptions.getTimeout();
    this.timeoutUnit = grpcOptions.getTimeoutUnit();
    this.closeClient = close;
  }

  public Future<GrpcClientRequest<Buffer, Buffer>> request(RequestOptions options) {
    return client.request(options)
      .map(httpRequest -> {
        GrpcClientRequestImpl<Buffer, Buffer> grpcRequest = new GrpcClientRequestImpl<>(httpRequest, scheduleDeadlineAutomatically, GrpcMessageEncoder.IDENTITY, GrpcMessageDecoder.IDENTITY);
        configureTimeout(grpcRequest);
        return grpcRequest;
      });
  }

  @Override
  public Future<GrpcClientRequest<Buffer, Buffer>> request() {
    return request(new RequestOptions().setMethod(HttpMethod.POST));
  }

  @Override
  public Future<GrpcClientRequest<Buffer, Buffer>> request(Address server) {
    return request(new RequestOptions().setMethod(HttpMethod.POST).setServer(server));
  }

  private void configureTimeout(GrpcClientRequest<?, ?> request) {
    ContextInternal current = (ContextInternal) vertx.getOrCreateContext();
    GrpcRequestLocal local = current.getLocal(GrpcRequestLocal.CONTEXT_LOCAL_KEY);
    long timeout = this.timeout;
    TimeUnit timeoutUnit = this.timeoutUnit;
    if (local != null) {
      timeout = local.deadline - System.currentTimeMillis();
      timeoutUnit = TimeUnit.MILLISECONDS;
      if (timeout < 0L) {
        throw new UnsupportedOperationException("Handle this case");
      }
    }
    request.timeout(timeout, timeoutUnit);
  }

  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(GrpcMessageDecoder<Resp> decoder, GrpcMessageEncoder<Req> encoder) {
    return request(new RequestOptions()
      .setMethod(HttpMethod.POST), decoder, encoder);
  }

  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(Address server, GrpcMessageDecoder<Resp> decoder, GrpcMessageEncoder<Req> encoder) {
    return request(new RequestOptions()
      .setMethod(HttpMethod.POST)
      .setServer(server), decoder, encoder);
  }

  private <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(RequestOptions options, GrpcMessageDecoder<Resp> messageDecoder, GrpcMessageEncoder<Req> messageEncoder) {
    return client.request(options)
      .map(request -> {
        GrpcClientRequestImpl<Req, Resp> call = new GrpcClientRequestImpl<>(request, scheduleDeadlineAutomatically, messageEncoder, messageDecoder);
        configureTimeout(call);
        return call;
      });
  }

  @Override
  public Future<Void> close() {
    if (closeClient) {
      return client.close();
    } else {
      return ((VertxInternal)vertx).getOrCreateContext().succeededFuture();
    }
  }
}
