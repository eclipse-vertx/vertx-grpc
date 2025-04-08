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

import io.grpc.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;

import java.util.concurrent.Executor;

/**
 * Bridge a gRPC service with a {@link io.vertx.grpc.client.GrpcClient}.
 */
public class GrpcIoClientChannel extends io.grpc.Channel {

  private GrpcIoClient client;
  private SocketAddress server;

  public GrpcIoClientChannel(GrpcClient client, SocketAddress server) {
    this.client = (GrpcIoClient) client;
    this.server = server;
  }

  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    String encoding = callOptions.getCompressor();
    Compressor compressor;
    if (encoding != null) {
      compressor = CompressorRegistry.getDefaultInstance().lookupCompressor(encoding);
    } else {
      compressor = null;
    }
    Executor exec = callOptions.getExecutor();
    Context ctx = Context.current();
    Deadline deadline = callOptions.getDeadline();
    Deadline contextDeadline = ctx.getDeadline();
    if (contextDeadline != null && (deadline == null || contextDeadline.isBefore(deadline))) {
      deadline = contextDeadline;
    }
    return new VertxClientCall<>(client, server, exec, methodDescriptor, encoding, compressor, deadline);
  }

  @Override
  public String authority() {
    return null;
  }

  public GrpcIoClient client() {
    return client;
  }
}
