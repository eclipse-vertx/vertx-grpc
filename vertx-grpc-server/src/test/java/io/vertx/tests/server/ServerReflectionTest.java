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

import io.grpc.*;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloWorldProto;
import io.grpc.reflection.test.ListServiceResponse;
import io.grpc.reflection.test.ServerReflectionGrpc;
import io.grpc.reflection.test.ServerReflectionRequest;
import io.grpc.reflection.test.ServerReflectionResponse;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.StreamObserver;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.ServiceMetadata;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerIndex;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerReflectionTest extends ServerTestBase {

  private static final ServiceMetadata SERVICE_METADATA = ServiceMetadata.metadata(GREETER, HelloWorldProto.getDescriptor().findServiceByName("Greeter"));

  @Test
  public void testReflection(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> {
        call.handler(helloRequest -> {
          HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
          GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
          response
            .encoding("identity")
            .end(helloReply);
        });
      }));

    channel = ManagedChannelBuilder.forAddress( "localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
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
