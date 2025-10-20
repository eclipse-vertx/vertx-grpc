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
import io.vertx.grpc.health.HealthService;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static dev.jbang.jash.Jash.$;

/**
 * This test requires grpc_health_probe to be installed on the system. It is excluded from the default test run and can be enabled by activating the "health-probe" profile.
 * <p>
 * Maven profile: mvn test -Phealth-probe
 */
public class HealthProbeTest extends GrpcTestBase {

  private HttpServer server;
  private HealthService healthService;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);

    Service greeterService = Service.service(GreeterGrpcService.SERVICE_NAME, HelloWorldProto.getDescriptor().findServiceByName("Greeter")).build();
    GrpcServer grpcServer = GrpcServer.server(vertx);

    healthService = HealthService.create(vertx);
    healthService.register(GreeterGrpcService.SERVICE_NAME, () -> Future.succeededFuture(true));

    grpcServer.addService(healthService);
    grpcServer.addService(greeterService);

    grpcServer.callHandler(GreeterGrpcService.SayHello, call -> call.handler(helloRequest -> {
      HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
      call.response().end(helloReply);
    }));

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
  public void testBasicHealthProbe(TestContext should) {
    Async async = should.async();

    Future<String> future = executeHealthProbe("");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("status: SERVING"),
        "Output should indicate SERVING status");
      async.complete();
    }));
  }

  @Test
  public void testServiceSpecificHealthProbe(TestContext should) {
    Async async = should.async();

    Future<String> future = executeHealthProbe("-service=" + GreeterGrpcService.SERVICE_NAME.fullyQualifiedName());

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("status: SERVING"),
        "Output should indicate SERVING status for " + GreeterGrpcService.SERVICE_NAME.fullyQualifiedName());
      async.complete();
    }));
  }

  @Test
  public void testUnknownServiceHealthProbe(TestContext should) {
    Async async = should.async();

    Future<String> future = executeHealthProbe("-service=unknown.UnknownService");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("NotFound"),
        "Output should indicate unknown service: " + output
      );
      async.complete();
    }));
  }

  @Test
  public void testNotServingHealthProbe(TestContext should) {
    Async async = should.async();

    // Register a service with NOT_SERVING status
    healthService.register("test.NotServingService", () -> Future.succeededFuture(false));

    Future<String> future = executeHealthProbe("-service=test.NotServingService");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("NOT_SERVING"),
        "Output should indicate NOT_SERVING status: " + output
      );
      async.complete();
    }));
  }

  @Test
  public void testHealthProbeWithUserAgent(TestContext should) {
    Async async = should.async();

    Future<String> future = executeHealthProbe("-user-agent=custom-health-probe/1.0");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("status: SERVING"),
        "Output should indicate SERVING status with custom user agent");
      async.complete();
    }));
  }

  @Test
  public void testHealthProbeWithTimeout(TestContext should) {
    Async async = should.async();

    Future<String> future = executeHealthProbe("-connect-timeout=5s -rpc-timeout=10s");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("status: SERVING"),
        "Output should indicate SERVING status with timeout options");
      async.complete();
    }));
  }

  @Test
  public void testHealthProbeWithGzip(TestContext should) {
    Async async = should.async();

    Future<String> future = executeHealthProbe("-gzip");

    future.onComplete(should.asyncAssertSuccess(output -> {
      should.assertTrue(output.contains("status: SERVING"),
        "Output should indicate SERVING status with gzip compression");
      async.complete();
    }));
  }

  private Future<String> executeHealthProbe(String args) {
    return vertx.executeBlocking(() -> {
      try {
        String command = "grpc_health_probe -addr=localhost:" + port + " " + args;
        System.out.println("[grpc_health_probe] Executing command: " + command);

        try (Jash process = $(command).withAllowedExitCodes(0, 3, 4).withTimeout(Duration.of(10, ChronoUnit.SECONDS))) {
          return process.stream().collect(Collectors.joining("\n"));
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to execute grpc_health_probe command", e);
      }
    });
  }
}
