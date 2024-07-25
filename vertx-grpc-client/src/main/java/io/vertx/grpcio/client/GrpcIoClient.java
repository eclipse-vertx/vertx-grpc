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
package io.vertx.grpcio.client;

import io.grpc.MethodDescriptor;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.impl.GrpcClientImpl;

import java.util.function.Function;

/**
 * <p>Extends the {@link GrpcClient} so it can be utilized with {@link MethodDescriptor}.</p>
 *
 * <p>In Vert.x 5, the core {@link GrpcClient} is decoupled from `io.grpc.*` packages to support JPMS.</p>
 */
@VertxGen
public interface GrpcIoClient extends GrpcClient {

  /**
   * Create a new client
   *
   * @param vertx the vertx instance
   * @return the created client
   */
  static GrpcIoClient client(Vertx vertx) {
    return new GrpcClientImpl(vertx);
  }

  /**
   * Create a new client
   *
   * @param vertx the vertx instance
   * @param options the client options
   * @return the created client
   */
  static GrpcIoClient client(Vertx vertx, HttpClientOptions options) {
    return new GrpcClientImpl(options, vertx);
  }

  /**
   * Call the {@code service} gRPC service hosted by {@code server}.
   * <p>
   *   The {@code requestHandler} is called to send the request, e.g. {@code req -> req.send(item)}
   * <p>
   *   The {@code responseFunction} extracts the result, e.g. {@code resp -> resp.last()}
   * <p>
   *   It can be used in various ways:
   * <ul>
   *   <li>{@code Future<Resp> fut = client.call(..., req -> req.send(item), resp -> resp.last());}</li>
   *   <li>{@code Future<Void> fut = client.call(..., req -> req.send(stream), resp -> resp.pipeTo(anotherService));}</li>
   *   <li>{@code Future<List<Resp>> fut = client.call(..., req -> req.send(stream), resp -> resp.collecting(Collectors.toList()));}</li>
   * </ul>
   * <pre>
   *
   * @param server the server hosting the service
   * @param service the service to call
   * @param requestHandler the handler called to send the request
   * @param resultFn the function applied to extract the result.
   * @return a future of the result
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @SuppressWarnings("deprecation")
  default <Req, Resp, T> Future<T> call(SocketAddress server, MethodDescriptor<Req, Resp> service, Handler<GrpcClientRequest<Req, Resp>> requestHandler, Function<GrpcClientResponse<Req, Resp>, Future<T>> resultFn) {
    return GrpcClient.super.call(server, service, requestHandler, resultFn);
  }
}
