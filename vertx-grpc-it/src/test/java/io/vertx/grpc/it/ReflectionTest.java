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
import org.junit.Test;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This test uses Testcontainers to run grpcurl in a Docker container.
 * It requires Docker to be installed and running.
 * <p>
 * Maven profile: mvn test -Pgrpcurl
 */
public class ReflectionTest extends TestContainerTestBase {

  private HttpServer server;
  private ImageFromDockerfile image;

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
      .listen(port, "0.0.0.0")
      .onComplete(should.asyncAssertSuccess(s -> {
        server = s;
        async.complete();
      }));
    async.awaitSuccess(10000);

    exposeHostPort();

    File dockerfile = new File("src/test/resources/grpcurl.Dockerfile");
    image = new ImageFromDockerfile().withFileFromFile("Dockerfile", dockerfile);
  }

  @Override
  public void tearDown(TestContext should) {
    if (server != null) {
      Async async = should.async();
      server.close().onComplete(v -> async.complete());
      async.awaitSuccess(10000);
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

    Future<String> future = executeGrpcurl("describe", "helloworld.Greeter");

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

    Future<String> future = executeGrpcurl("describe", ".helloworld.HelloRequest");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("string name"), "Output should contain name field");
      async.complete();
    }));
  }

  private Future<String> executeGrpcurl(String... args) {
    List<String> command = new ArrayList<>();
    command.add("-plaintext");
    command.add(HOST_INTERNAL + ":" + port);
    Collections.addAll(command, args);

    return executeInContainer(image, command);
  }
}
