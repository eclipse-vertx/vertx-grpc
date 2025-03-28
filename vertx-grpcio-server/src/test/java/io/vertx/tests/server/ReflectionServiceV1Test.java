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
package io.vertx.tests.server;

import io.grpc.ManagedChannelBuilder;
import io.vertx.tests.reflection.grpc.ListServiceResponse;
import io.vertx.tests.reflection.grpc.ServerReflectionGrpc;
import io.vertx.tests.reflection.grpc.ServerReflectionRequest;
import io.vertx.tests.reflection.grpc.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.reflection.ReflectionService;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestConstants;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Test;

public class ReflectionServiceV1Test extends ServerTestBase {

  @Test
  public void testReflection(TestContext should) {
    // server stub
    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    // create grpc server handler
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);
    grpcServer.addService(ReflectionService.v1());

    // bind server stub
    GrpcIoServiceBridge bridge = GrpcIoServiceBridge.bridge(impl);
    grpcServer.addService(bridge);

    // start server
    startServer(grpcServer);

    // set up client stub
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);

    // set up response observer
    Async test = should.async();
    StreamObserver<ServerReflectionRequest> streamObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse serverReflectionResponse) {
        ListServiceResponse response = serverReflectionResponse.getListServicesResponse();
        should.assertEquals(2, response.getServiceCount());
        response.getServiceList().stream().filter(service -> service.getName().equals(TestConstants.TEST_SERVICE.fullyQualifiedName())).findFirst().orElseThrow();
        response.getServiceList().stream().filter(service -> service.getName().equals("grpc.reflection.v1.ServerReflection")).findFirst().orElseThrow();
      }

      @Override
      public void onError(Throwable throwable) {
        should.fail(throwable);
      }

      @Override
      public void onCompleted() {
        test.complete();
      }
    });

    // send request
    ServerReflectionRequest request = ServerReflectionRequest.newBuilder().setListServices("").build();
    streamObserver.onNext(request);

    // wait for test completion
    test.await();
  }
}
