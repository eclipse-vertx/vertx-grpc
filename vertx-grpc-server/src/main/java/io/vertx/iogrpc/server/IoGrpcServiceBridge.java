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
package io.vertx.iogrpc.server;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServiceBridge;
import io.vertx.grpc.server.impl.GrpcServiceBridgeImpl;

/**
 * Bridge a gRPC service with a {@link GrpcServer}.
 */
public interface IoGrpcServiceBridge extends GrpcServiceBridge {

  /**
   * Create a stub for a given {@code service}.
   *
   * @param service the service
   * @return the stub
   */
  static IoGrpcServiceBridge bridge(ServerServiceDefinition service) {
    return new GrpcServiceBridgeImpl(service);
  }

  /**
   * Create a stub for a given {@code service}.
   *
   * @param service the service
   * @return the stub
   */
  static IoGrpcServiceBridge bridge(BindableService service) {
    return bridge(service.bindService());
  }

}
