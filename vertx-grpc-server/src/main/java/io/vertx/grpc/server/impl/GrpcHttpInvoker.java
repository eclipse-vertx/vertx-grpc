/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.server.impl;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.grpc.common.ServiceMethod;

/**
 * An interface that defines the behavior for invoking gRPC services based on an incoming HTTP request. This is designed to bridge HTTP requests to corresponding gRPC service
 * methods by creating a {@link GrpcInvocation} instance that facilitates the interaction.
 */
public interface GrpcHttpInvoker {

  /**
   * Accepts an incoming HTTP server request and associates it with a gRPC service method. This method creates a {@code GrpcInvocation} instance to facilitate communication between
   * the HTTP request and the specified gRPC service method.
   *
   * @param <Req> the type of the request message for the gRPC service method
   * @param <Resp> the type of the response message for the gRPC service method
   * @param request the HTTP server request to be processed
   * @param serviceMethod the gRPC service method that corresponds to the incoming request
   * @return an instance of {@code GrpcInvocation} that represents the invocation of the gRPC service method
   */
  <Req, Resp> GrpcInvocation<Req, Resp> accept(HttpServerRequest request, ServiceMethod<Req, Resp> serviceMethod);

}
