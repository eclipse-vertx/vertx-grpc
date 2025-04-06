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
package io.vertx.grpc.it;

import com.google.protobuf.ByteString;
import io.grpc.examples.helloworld.*;
import io.grpc.testing.integration.*;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.Service;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProtocPluginTest extends ProtocPluginTestBase {

  @Override
  protected GrpcServer grpcServer() {
    return GrpcServer.server(vertx);
  }

  @Override
  protected GrpcClient grpcClient() {
    return GrpcClient.client(vertx);
  }

  @Override
  protected Service greeterService(GreeterService service) {
    return GreeterGrpcService.of(service);
  }

  @Override
  protected GreeterClient greeterClient(GrpcClient grpcClient, SocketAddress socketAddress) {
    return GreeterGrpcClient.create(grpcClient, socketAddress);
  }

  @Override
  protected Service testService(TestServiceService service) {
    return TestServiceGrpcService.of(service);
  }

  @Override
  protected TestServiceClient testClient(GrpcClient client, SocketAddress socketAddress) {
    return TestServiceGrpcClient.create(client, socketAddress);
  }
}
