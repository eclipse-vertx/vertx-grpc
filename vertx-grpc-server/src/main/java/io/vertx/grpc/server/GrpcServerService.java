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
package io.vertx.grpc.server;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.grpc.common.ServiceMethod;

import java.util.List;

/**
 * A contract for registering gRPC service method handlers, without requiring
 * a specific transport.
 *
 * <p>Generated gRPC service stubs use this interface as their binding abstraction,
 * allowing them to work with any implementation - HTTP/2 via {@link GrpcServer}, event bus, etc.</p>
 */
@VertxGen
public interface GrpcServerService {

  /**
   * Set a service method call handler.
   *
   * @param serviceMethod the service method
   * @param handler the service method call handler
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> GrpcServerService callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler);

  /**
   * Add a service to this server.
   *
   * @param service the service to add
   * @return a reference to this, so the API can be used fluently
   * @throws IllegalStateException if a service with the same name is already registered
   */
  GrpcServerService addService(Service service);

  /**
   * Get a list of all services registered with this server.
   *
   * @return an unmodifiable list of all registered services
   */
  List<Service> services();

}
