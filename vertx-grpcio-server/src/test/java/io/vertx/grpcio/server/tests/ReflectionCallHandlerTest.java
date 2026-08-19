/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpcio.server.tests;

import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.vertx.grpc.reflection.ReflectionService;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.Service;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestConstants;
import io.vertx.grpc.common.tests.TestServiceGrpc;
import io.vertx.tests.reflection.grpc.ListServiceResponse;
import io.vertx.tests.reflection.grpc.ServerReflectionGrpc;
import io.vertx.tests.reflection.grpc.ServerReflectionRequest;
import io.vertx.tests.reflection.grpc.ServerReflectionResponse;
import io.vertx.grpc.server.tests.ServerTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ReflectionCallHandlerTest extends ServerTestBase {

  @Test
  public void testCallHandlerVisibleInReflection() throws Exception {
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);
    grpcServer.addService(ReflectionService.v1());

    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), call -> {
      call.handler(request -> {
        Reply reply = Reply.newBuilder().setMessage("Hello " + request.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response.encoding("identity").end(reply);
      });
    });

    startServer(grpcServer);
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    StreamObserver<ServerReflectionRequest> streamObserver = ServerReflectionGrpc.newStub(channel).serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        try {
          ListServiceResponse listResponse = response.getListServicesResponse();
          assertEquals(2, listResponse.getServiceCount());
          listResponse.getServiceList().stream()
            .filter(service -> service.getName().equals(TestConstants.TEST_SERVICE.fullyQualifiedName()))
            .findFirst().orElseThrow();
          listResponse.getServiceList().stream()
            .filter(service -> service.getName().equals("grpc.reflection.v1.ServerReflection"))
            .findFirst().orElseThrow();
        } catch (Throwable t) {
          failure.set(t);
          latch.countDown();
        }
      }
      @Override
      public void onError(Throwable throwable) {
        failure.set(throwable);
        latch.countDown();
      }
      @Override
      public void onCompleted() {
        latch.countDown();
      }
    });
    streamObserver.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
    streamObserver.onCompleted();

    assertTrue("Timed out waiting for reflection response", latch.await(10, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  @Test
  public void testCallHandlerNotDuplicatedWithAddService() throws Exception {
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);
    grpcServer.addService(ReflectionService.v1());
    grpcServer.addService(
      io.vertx.grpc.server.Service.service(
        TestConstants.TEST_SERVICE,
        io.vertx.grpc.common.tests.Tests.getDescriptor().findServiceByName("TestService")
      ).build()
    );
    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), call -> {
      call.handler(request -> {
        Reply reply = Reply.newBuilder().setMessage("Hello " + request.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response.encoding("identity").end(reply);
      });
    });

    startServer(grpcServer);
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    StreamObserver<ServerReflectionRequest> streamObserver = ServerReflectionGrpc.newStub(channel).serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        try {
          // Should be 2, not 3 - callHandler service should not duplicate the addService entry
          assertEquals(2, response.getListServicesResponse().getServiceCount());
        } catch (Throwable t) {
          failure.set(t);
          latch.countDown();
        }
      }
      @Override
      public void onError(Throwable throwable) {
        failure.set(throwable);
        latch.countDown();
      }
      @Override
      public void onCompleted() {
        latch.countDown();
      }
    });
    streamObserver.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
    streamObserver.onCompleted();

    assertTrue("Timed out waiting for reflection response", latch.await(10, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  @Test
  public void testCallHandlerOnlyExposesRegisteredMethods() {
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    // TestService defines Unary, Source, Sink and Pipe — register only Unary
    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), call -> {});

    Service testService = grpcServer.services().stream()
      .filter(s -> s.name().fullyQualifiedName().equals(TestConstants.TEST_SERVICE.fullyQualifiedName()))
      .findFirst()
      .orElseThrow();

    assertEquals(1, testService.methodDescriptors().size());
    assertEquals("Unary", testService.methodDescriptors().get(0).getName());
    assertTrue(testService.hasMethod("Unary"));
    assertFalse(testService.hasMethod("Source"));
    assertFalse(testService.hasMethod("Sink"));
    assertFalse(testService.hasMethod("Pipe"));
  }

  @Test
  public void testCallHandlerUnregistrationRemovesService() {
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);
    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), call -> {});

    assertTrue(grpcServer.services().stream()
      .anyMatch(s -> s.name().fullyQualifiedName().equals(TestConstants.TEST_SERVICE.fullyQualifiedName())));

    // Passing a null handler is the unregistration path.
    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), null);

    assertFalse(grpcServer.services().stream().anyMatch(s -> s.name().fullyQualifiedName().equals(TestConstants.TEST_SERVICE.fullyQualifiedName())));
  }

  @Test
  public void testCallHandlerReRegistrationDoesNotDuplicate() {
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);
    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), call -> {});
    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), call -> {});

    Service testService = grpcServer.services().stream()
      .filter(s -> s.name().fullyQualifiedName().equals(TestConstants.TEST_SERVICE.fullyQualifiedName()))
      .findFirst()
      .orElseThrow();

    assertEquals(1, testService.methodDescriptors().size());
    assertEquals("Unary", testService.methodDescriptors().get(0).getName());
  }

  @Test
  public void testCallHandlerFileContainingSymbol() throws Exception {
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);
    grpcServer.addService(ReflectionService.v1());

    grpcServer.callHandler(TestServiceGrpc.getUnaryMethod(), call -> {
      call.handler(request -> {
        Reply reply = Reply.newBuilder().setMessage("Hello " + request.getName()).build();
        call.response().encoding("identity").end(reply);
      });
    });

    startServer(grpcServer);
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    StreamObserver<ServerReflectionRequest> streamObserver = ServerReflectionGrpc.newStub(channel).serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        try {
          assertTrue(response.hasFileDescriptorResponse());
          assertTrue(response.getFileDescriptorResponse().getFileDescriptorProtoCount() > 0);
        } catch (Throwable t) {
          failure.set(t);
          latch.countDown();
        }
      }
      @Override
      public void onError(Throwable throwable) {
        failure.set(throwable);
        latch.countDown();
      }
      @Override
      public void onCompleted() {
        latch.countDown();
      }
    });
    streamObserver.onNext(ServerReflectionRequest.newBuilder()
      .setFileContainingSymbol(TestConstants.TEST_SERVICE.fullyQualifiedName())
      .build());
    streamObserver.onCompleted();

    assertTrue("Timed out waiting for reflection response", latch.await(10, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }
}
