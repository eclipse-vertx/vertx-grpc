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

package io.vertx.tests.server.web.interop;

import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.iogrpc.server.IoGrpcServer;
import io.vertx.iogrpc.server.IoGrpcServiceBridge;

/**
 * A gRPC-Web server for grpc-web interop tests.
 */
public class InteropServer extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new InteropServer())
      .onFailure(Throwable::printStackTrace)
      .onSuccess(v -> System.out.println("Deployed InteropServer"));
  }

  @Override
  public void start(Promise<Void> startPromise) {
    IoGrpcServer grpcServer = IoGrpcServer.server(vertx, new GrpcServerOptions().setGrpcWebEnabled(true));

    ServerServiceDefinition serviceDefinition = ServerInterceptors.intercept(new TestServiceImpl(vertx), new Interceptor());
    IoGrpcServiceBridge.bridge(serviceDefinition).bind(grpcServer);

    vertx.createHttpServer()
      .requestHandler(grpcServer)
      .listen(8080)
      .<Void>mapEmpty()
      .onComplete(startPromise);
  }
}
