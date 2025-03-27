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

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.grpcio.server.impl.GrpcIoServiceBridgeImpl;

/**
 * Bridge a gRPC service with a {@link GrpcServer}.
 */
public interface GrpcIoServiceBridge extends Service {

  /**
   * Create a stub for a given {@code service}.
   *
   * @param service the service
   * @return the stub
   */
  static GrpcIoServiceBridge bridge(ServerServiceDefinition service) {
    return new GrpcIoServiceBridgeImpl(service);
  }

  /**
   * Create a stub for a given {@code service}.
   *
   * @param service the service
   * @return the stub
   */
  static GrpcIoServiceBridge bridge(BindableService service) {
    return bridge(service.bindService());
  }

  /**
   * Bind all service methods to the @{code server}.
   *
   * @param server the server to bind to
   */
  void bind(GrpcIoServer server);

  @Override
  default void bind(GrpcServer server) {
    bind((GrpcIoServer) server);
  }

  /**
   * Unbind all service methods from the @{code server}.
   *
   * @param server the server to unbind from
   */
  void unbind(GrpcIoServer server);

}
