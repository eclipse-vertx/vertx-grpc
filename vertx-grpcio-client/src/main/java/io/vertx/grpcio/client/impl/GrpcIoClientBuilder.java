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
package io.vertx.grpcio.client.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.impl.GrpcClientBuilderImpl;
import io.vertx.grpcio.client.GrpcIoClient;

public class GrpcIoClientBuilder extends GrpcClientBuilderImpl<GrpcIoClient> {

  public GrpcIoClientBuilder(Vertx vertx) {
    super(vertx);
  }

  @Override
  protected GrpcIoClient create(Vertx vertx, GrpcClientOptions options, HttpClient transport) {
    return new GrpcIoClientImpl(vertx, options, transport, true);
  }
}
