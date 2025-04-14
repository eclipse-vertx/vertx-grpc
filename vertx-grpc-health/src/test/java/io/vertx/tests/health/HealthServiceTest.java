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
package io.vertx.tests.health;

import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.health.HealthService;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.tests.common.grpc.TestConstants;
import io.vertx.tests.health.grpc.*;
import io.vertx.tests.server.ServerTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class HealthServiceTest extends ServerTestBase {

  @Test
  public void testHealthCheck(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    HealthService service = HealthService.create(vertx);

    // Register a service with OK status
    service.register(TestConstants.TEST_SERVICE, () -> Future.succeededFuture(true));

    startServer(GrpcServer
      .server(vertx)
      .addService(service));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    HealthGrpc.HealthStub stub = HealthGrpc.newStub(channel);

    HealthCheckRequest request = HealthCheckRequest.newBuilder()
      .setService(TestConstants.TEST_SERVICE.fullyQualifiedName())
      .build();

    stub.check(request, new StreamObserver<>() {
      @Override
      public void onNext(HealthCheckResponse response) {
        should.assertEquals(HealthCheckResponse.ServingStatus.SERVING, response.getStatus());
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

    test.await();
  }

  @Test
  public void testHealthCheckUnknownService(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    HealthService healthService = HealthService.create(vertx);

    startServer(GrpcServer
      .server(vertx)
      .addService(healthService));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    HealthGrpc.HealthStub stub = HealthGrpc.newStub(channel);

    HealthCheckRequest request = HealthCheckRequest.newBuilder()
      .setService("unknown.service")
      .build();

    AtomicBoolean errorReceived = new AtomicBoolean(false);

    stub.check(request, new StreamObserver<>() {
      @Override
      public void onNext(HealthCheckResponse response) {
        should.assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, response.getStatus());
      }

      @Override
      public void onError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
          StatusRuntimeException sre = (StatusRuntimeException) throwable;
          should.assertEquals(Status.NOT_FOUND.getCode(), sre.getStatus().getCode());
          errorReceived.set(true);
          test.complete();
          return;
        }

        should.fail(throwable);
      }

      @Override
      public void onCompleted() {
        if (!errorReceived.get()) {
          test.complete();
        }
      }
    });

    test.await();
  }

  @Test
  public void testHealthList(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    HealthService service = HealthService.create(vertx);

    // Register a service with OK status
    service.register(TestConstants.TEST_SERVICE, () -> Future.succeededFuture(true));

    startServer(GrpcServer
      .server(vertx)
      .addService(service));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    HealthGrpc.HealthStub stub = HealthGrpc.newStub(channel);
    HealthListRequest request = HealthListRequest.newBuilder().build();

    stub.list(request, new StreamObserver<>() {
      @Override
      public void onNext(HealthListResponse response) {
        should.assertEquals(2, response.getStatusesCount());
        should.assertTrue(response.getStatusesMap().containsKey(TestConstants.TEST_SERVICE.fullyQualifiedName()));

        for (String serviceName : response.getStatusesMap().keySet()) {
          HealthCheckResponse status = response.getStatusesMap().get(serviceName);
          should.assertNotNull(status);
          should.assertEquals(HealthCheckResponse.ServingStatus.SERVING, status.getStatus());
        }
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

    test.await();
  }

  @Test
  public void testHealthWatch(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    final AtomicReference<Boolean> status = new AtomicReference<>(true);
    final HealthService service = HealthService.create(vertx);

    // Register a service with OK status
    service.register(TestConstants.TEST_SERVICE, () -> Future.succeededFuture(status.get()));

    startServer(GrpcServer
      .server(vertx)
      .addService(service));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    HealthGrpc.HealthStub stub = HealthGrpc.newStub(channel);

    HealthCheckRequest request = HealthCheckRequest.newBuilder()
      .setService(TestConstants.TEST_SERVICE.fullyQualifiedName())
      .build();

    List<HealthCheckResponse> responses = new ArrayList<>();

    stub.watch(request, new StreamObserver<>() {
      @Override
      public void onNext(HealthCheckResponse response) {
        responses.add(response);

        // After receiving the initial response, change the service status
        if (responses.size() == 1) {
          should.assertEquals(HealthCheckResponse.ServingStatus.SERVING, response.getStatus());

          // Change the status to NOT_SERVING
          status.set(false);
        } else if (responses.size() == 2) {
          should.assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, response.getStatus());
          test.complete();
        }
      }

      @Override
      public void onError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
          StatusRuntimeException sre = (StatusRuntimeException) throwable;
          should.assertEquals(Status.UNAVAILABLE.getCode(), sre.getStatus().getCode());
          return;
        }

        should.fail(throwable);
      }

      @Override
      public void onCompleted() {
        // This shouldn't be called unless we explicitly close the stream
      }
    });

    test.await(10000); // Give more time for the watch test
  }

  @Test
  public void testHealthWatchUnknownService(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    HealthService healthService = HealthService.create(vertx);

    startServer(GrpcServer
      .server(vertx)
      .addService(healthService));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    HealthGrpc.HealthStub stub = HealthGrpc.newStub(channel);

    HealthCheckRequest request = HealthCheckRequest.newBuilder()
      .setService("unknown.service")
      .build();

    stub.watch(request, new StreamObserver<>() {
      @Override
      public void onNext(HealthCheckResponse response) {
        if (response.getStatus() == HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN) {
          should.assertEquals(HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN, response.getStatus());
          healthService.register("unknown.service", () -> Future.succeededFuture(true));
        } else {
          should.assertEquals(HealthCheckResponse.ServingStatus.SERVING, response.getStatus());
          test.complete();
        }
      }

      @Override
      public void onError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
          StatusRuntimeException sre = (StatusRuntimeException) throwable;
          should.assertEquals(Status.UNAVAILABLE.getCode(), sre.getStatus().getCode());
          return;
        }

        should.fail(throwable);
      }

      @Override
      public void onCompleted() {
        // This shouldn't be called unless we explicitly close the stream
      }
    });

    // Set a timer to complete the test after a delay
    vertx.setTimer(5000, id -> test.complete());

    test.await(10000); // Give more time for the watch test
  }
}
