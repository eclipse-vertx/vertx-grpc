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
package io.vertx.grpc.server;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.server.impl.GrpcServerImpl;

import java.util.List;

/**
 * <p>A gRPC server based on Vert.x HTTP server.</p>
 *
 * <p>The server can be used as a {@link io.vertx.core.http.HttpServer} handler or mounted as a Vert.x Web handler.</p>
 *
 * <p>Unlike traditional gRPC servers, this server does not rely on a generated RPC interface to interact with the service.</p>
 *
 * <p>Instead, you can interact with the service with a request/response interfaces and gRPC messages, very much like
 * a traditional client.</p>
 *
 * <p>The server handles only the gRPC protocol and does not encode/decode protobuf messages.</p>
 */
@VertxGen
public interface GrpcServer extends Handler<HttpServerRequest> {

  /**
   * Create a blank gRPC server with default options.
   *
   * @return the created server
   */
  static GrpcServer server(Vertx vertx) {
    return server(vertx, new GrpcServerOptions());
  }

  /**
   * Create a blank gRPC server with specified options.
   *
   * @param options the gRPC server options
   * @return the created server
   */
  static GrpcServer server(Vertx vertx, GrpcServerOptions options) {
    return new GrpcServerImpl(vertx, options);
  }

  /**
   * Set a call handler that handles any call made to the server.
   *
   * @param handler the service method call handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  GrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler);

  /**
   * Set a service method call handler that handles any call made to the server for the {@code fullMethodName } service method.
   *
   * @param handler the service method call handler
   * @param serviceMethod the service method
   * @return a reference to this, so the API can be used fluently
   */
  <Req, Resp> GrpcServer callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler);

/*
  */
/**
   * Set a service method call handler that handles any call made to the server for the {@code fullMethodName } service method.
   * You can use this method to bind a service method and pass the transcoding options.
   *
   * @param handler the service method call handler
   * @param serviceMethod the service method
   * @param transcodingOptions the transcoding options
   * @return a reference to this, so the API can be used fluently
   *//*

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> GrpcServer callHandlerWithTranscoding(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler, MethodTranscodingOptions transcodingOptions);
*/

  GrpcServer addService(Service service);

  List<Service> getServices();
}
