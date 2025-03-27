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
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.reflection.test.ListServiceResponse;
import io.grpc.reflection.test.ServerReflectionGrpc;
import io.grpc.reflection.test.ServerReflectionRequest;
import io.grpc.reflection.test.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.reflection.ReflectionService;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import org.junit.Test;

public class ReflectionServiceV1Test extends ServerTestBase {

  @Test
  public void testReflection(TestContext should) {
    // server stub
    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    // create grpc server handler
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);
    grpcServer.addService(new ReflectionService());

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
        response.getServiceList().stream().filter(service -> service.getName().equals("helloworld.Greeter")).findFirst().orElseThrow();
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
