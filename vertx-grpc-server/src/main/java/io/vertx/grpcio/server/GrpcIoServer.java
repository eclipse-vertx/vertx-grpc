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
package io.vertx.grpcio.server;

import io.grpc.MethodDescriptor;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.impl.GrpcServerImpl;

/**
 * <p>Extends the {@link GrpcServer} so it can be utilized with {@link MethodDescriptor}.</p>
 *
 * <p>In Vert.x 5, the core {@link GrpcServer} is decoupled from `io.grpc.*` packages to support JPMS.</p>
 */
@VertxGen
public interface GrpcIoServer extends GrpcServer {

  /**
   * Create a blank gRPC/IO server
   *
   * @param vertx the vertx instance
   * @return the created server
   */
  static GrpcIoServer server(Vertx vertx) {
    return server(vertx, new GrpcServerOptions());
  }

  /**
   * Create a blank gRPC/IO server
   *
   * @param vertx the vertx instance
   * @param options the server options
   * @return the created server
   */
  static GrpcIoServer server(Vertx vertx, GrpcServerOptions options) {
    return new GrpcServerImpl(vertx, options);
  }

  /**
   * Set a service method call handler that handles any call made to the server for the {@link MethodDescriptor} service method.
   *
   * @param handler the service method call handler
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> GrpcIoServer callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler);

}
