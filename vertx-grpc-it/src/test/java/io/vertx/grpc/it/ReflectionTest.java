/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.it;

import dev.jbang.jash.Jash;
import io.grpc.examples.helloworld.GreeterGrpcService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloWorldProto;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.reflection.ReflectionService;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import static dev.jbang.jash.Jash.$;

/**
 * This test requires grpcurl to be installed on the system. It is excluded from the default test run and can be enabled by activating the "grpcurl"
 * <p>
 * Maven profile: mvn test -Pgrpcurl
 */
public class ReflectionTest extends GrpcTestBase {

  private HttpServer server;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);

    Service greeterService = Service.service(GreeterGrpcService.SERVICE_NAME, HelloWorldProto.getDescriptor().findServiceByName("Greeter")).build();
    GrpcServer grpcServer = GrpcServer.server(vertx);

    grpcServer.addService(ReflectionService.v1());
    grpcServer.addService(greeterService);

    grpcServer.callHandler(GreeterGrpcService.SayHello, call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      });
    });

    Async async = should.async();
    vertx.createHttpServer()
      .requestHandler(grpcServer)
      .listen(port, "localhost")
      .onComplete(should.asyncAssertSuccess(s -> {
        server = s;
        async.complete();
      }));
    async.awaitSuccess(5000);
  }

  @Override
  public void tearDown(TestContext should) {
    if (server != null) {
      Async async = should.async();
      server.close().onComplete(v -> async.complete());
      async.awaitSuccess(5000);
    }
    super.tearDown(should);
  }

  @Test
  public void testReflectionListServices(TestContext should) {
    Async async = should.async();

    Future<String> future = executeGrpcurl("list");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("helloworld.Greeter"), "Output should contain helloworld.Greeter service");
      should.assertTrue(output.contains("grpc.reflection.v1.ServerReflection"), "Output should contain reflection service");
      async.complete();
    }));
  }

  @Test
  public void testReflectionDescribeService(TestContext should) {
    Async async = should.async();

    Future<String> future = executeGrpcurl("describe helloworld.Greeter");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("rpc SayHello"), "Output should contain SayHello method");
      should.assertTrue(output.contains("HelloRequest"), "Output should contain HelloRequest message");
      should.assertTrue(output.contains("HelloReply"), "Output should contain HelloReply message");
      async.complete();
    }));
  }

  @Test
  public void testReflectionDescribeMessage(TestContext should) {
    Async async = should.async();

    Future<String> future = executeGrpcurl("describe .helloworld.HelloRequest");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("string name"), "Output should contain name field");
      async.complete();
    }));
  }

  private Future<String> executeGrpcurl(String args) {
    return vertx.executeBlocking(() -> {
      try {
        String command = "grpcurl -plaintext localhost:" + port + " " + args;
        System.out.println("[grpcurl] Executing command: " + command);

        try (Jash process = $(command).withTimeout(Duration.of(10, ChronoUnit.SECONDS))) {
          return process.stream().collect(Collectors.joining("\n"));
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to execute grpcurl command", e);
      }
    });
  }
}
