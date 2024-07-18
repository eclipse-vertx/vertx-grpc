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
package io.vertx.iogrpc.client;

import io.grpc.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientChannel;

/**
 * Bridge a gRPC service with a {@link GrpcClient}.
 */
public class IoGrpcClientChannel extends GrpcClientChannel {

  public IoGrpcClientChannel(IoGrpcClient client, SocketAddress server) {
    super(client, server);
  }

  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    return super.newCall(methodDescriptor, callOptions);
  }

  @Override
  public String authority() {
    return null;
  }
}