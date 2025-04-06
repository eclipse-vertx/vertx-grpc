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

import io.grpc.examples.helloworld.*;
import io.grpc.testing.integration.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.server.GrpcIoServer;

public class ProtocPluginStubTest extends ProtocPluginTestBase {

  @Override
  protected GrpcServer grpcServer() {
    return GrpcIoServer.server(vertx);
  }

  @Override
  protected GrpcClient grpcClient() {
    return GrpcIoClient.client(vertx);
  }

  @Override
  protected Service greeterService(GreeterService service) {
    return GreeterGrpcIo.of(service);
  }

  @Override
  protected GreeterClient greeterClient(GrpcClient grpcClient, SocketAddress socketAddress) {
    return GreeterGrpcIo.newStub((GrpcIoClient)grpcClient, socketAddress);
  }

  @Override
  protected Service testService(TestServiceService service) {
    return TestServiceGrpcIo.of(service);
  }

  @Override
  protected TestServiceClient testClient(GrpcClient grpcClient, SocketAddress socketAddress) {
    return TestServiceGrpcIo.newStub((GrpcIoClient) grpcClient, socketAddress);
  }
}
