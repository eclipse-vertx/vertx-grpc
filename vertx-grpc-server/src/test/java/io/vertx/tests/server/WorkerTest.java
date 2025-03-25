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
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import org.junit.Test;

public class WorkerTest extends ServerTestBase {

  @Test
  public void testWorker(TestContext should) throws Exception {
    ContextInternal worker = ((VertxInternal) vertx).createWorkerContext();
    Async latch = should.async();
    worker.runOnContext(v -> {
      HttpServer httpServer = createServer(GrpcServer
        .server(vertx)
        .callHandler(GREETER_SAY_HELLO, call -> {
        call.handler(helloRequest -> {
          HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
          GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
          response.end(helloReply);
        });
      }));
      httpServer.listen().onComplete(should.asyncAssertSuccess(v2 -> latch.complete()));
    });
    latch.awaitSuccess();

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

    HelloRequest request = HelloRequest.newBuilder().setName("Julien").build();
    HelloReply res = stub.sayHello(request);
    should.assertEquals("Hello Julien", res.getMessage());
  }
}
