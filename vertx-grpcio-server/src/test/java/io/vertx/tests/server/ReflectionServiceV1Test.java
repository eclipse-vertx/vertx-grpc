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
import io.grpc.reflection.v1.ListServiceResponse;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import io.vertx.grpcio.server.GrpcServerIndex;
import io.vertx.grpcio.server.ReflectionServiceV1Handler;
import org.junit.Test;

import java.util.List;

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

    // bind server stub
    GrpcIoServiceBridge bridge = GrpcIoServiceBridge.bridge(impl);
    bridge.bind(grpcServer);

    // create grpc proto index used by reflection service
    GrpcServerIndex index = new GrpcServerIndex(List.of(impl.bindService()));

    // register proto reflection service handler
    grpcServer.callHandler(ServerReflectionGrpc.getServerReflectionInfoMethod(),
      new ReflectionServiceV1Handler(index));

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
        ListServiceResponse listServicesResponse = serverReflectionResponse.getListServicesResponse();
        should.assertEquals(1, listServicesResponse.getServiceCount());
        should.assertEquals("helloworld.Greeter", listServicesResponse.getService(0).getName());
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
