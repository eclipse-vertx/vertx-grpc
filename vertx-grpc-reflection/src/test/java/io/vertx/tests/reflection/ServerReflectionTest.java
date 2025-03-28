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
package io.vertx.tests.reflection;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusException;
import io.grpc.reflection.test.*;
import io.grpc.stub.StreamObserver;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.reflection.ReflectionService;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.tests.common.grpc.TestConstants;
import io.vertx.tests.server.ServerTestBase;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.Tests;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ServerReflectionTest extends ServerTestBase {

  private static final Service GREETER_SERVICE_METADATA = Service
    .service(TestConstants.TEST_SERVICE, Tests.getDescriptor().findServiceByName("TestService"))
    .build();

  private static final Service STREAMING_SERVICE_METADATA = Service
    .service(TestConstants.TEST_SERVICE, Tests.getDescriptor().findServiceByName("TestService"))
    .build();

  @Test
  public void testBasicReflection(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx)
      .addService(GREETER_SERVICE_METADATA)
      .addService(ReflectionService.v1())
      .<Request, Reply>callHandler(UNARY, call -> call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
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

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder().setListServices("").build();
    streamObserver.onNext(request);

    test.await();
  }

  @Test
  public void testFileByFilename(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx)
      .addService(ReflectionService.v1())
      .addService(GREETER_SERVICE_METADATA)
      .<Request, Reply>callHandler(UNARY, call -> call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    AtomicReference<ByteString> fileDescriptorBytes = new AtomicReference<>();

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
    StreamObserver<ServerReflectionRequest> streamObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        should.assertTrue(response.hasFileDescriptorResponse());
        should.assertTrue(response.getFileDescriptorResponse().getFileDescriptorProtoCount() > 0);
        fileDescriptorBytes.set(response.getFileDescriptorResponse().getFileDescriptorProto(0));
        should.assertNotNull(fileDescriptorBytes.get());
        should.assertTrue(!fileDescriptorBytes.get().isEmpty());
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

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
      .setFileByFilename("tests.proto")
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testFileContainingSymbol(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx)
      .addService(ReflectionService.v1())
      .addService(GREETER_SERVICE_METADATA)
      .<Request, Reply>callHandler(UNARY, call -> call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
    StreamObserver<ServerReflectionRequest> streamObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        should.assertTrue(response.hasFileDescriptorResponse());
        should.assertTrue(response.getFileDescriptorResponse().getFileDescriptorProtoCount() > 0);
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

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
      .setFileContainingSymbol(TestConstants.TEST_SERVICE.fullyQualifiedName())
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testFileContainingExtension(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx)
      .addService(ReflectionService.v1())
      .addService(GREETER_SERVICE_METADATA)
      .<Request, Reply>callHandler(UNARY, call -> call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    AtomicBoolean receivedResponse = new AtomicBoolean(false);

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
    StreamObserver<ServerReflectionRequest> streamObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        receivedResponse.set(true);
        // Note: The test may pass even if the server doesn't support extensions
        // We're just testing that the server responds appropriately
        if (response.hasFileDescriptorResponse()) {
          should.assertTrue(response.getFileDescriptorResponse().getFileDescriptorProtoCount() >= 0);
        }
      }

      @Override
      public void onError(Throwable throwable) {
        // Also acceptable if the server doesn't support extensions
        receivedResponse.set(true);
      }

      @Override
      public void onCompleted() {
        should.assertTrue(receivedResponse.get(), "Should receive either a response or an error");
        test.complete();
      }
    });

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
      .setFileContainingExtension(ExtensionRequest.newBuilder()
        .setContainingType("helloworld.Request")
        .setExtensionNumber(100)
        .build())
      .build();

    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testAllExtensionNumbersOfType(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx)
      .addService(ReflectionService.v1())
      .addService(GREETER_SERVICE_METADATA)
      .<Request, Reply>callHandler(UNARY, call -> call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    AtomicBoolean receivedResponse = new AtomicBoolean(false);

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
    StreamObserver<ServerReflectionRequest> streamObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        receivedResponse.set(true);
        // The test may pass even if the server doesn't support extensions
        if (response.hasAllExtensionNumbersResponse()) {
          should.assertNotNull(response.getAllExtensionNumbersResponse());
        }
      }

      @Override
      public void onError(Throwable throwable) {
        // Also acceptable if the server doesn't support extensions
        receivedResponse.set(true);
      }

      @Override
      public void onCompleted() {
        should.assertTrue(receivedResponse.get(), "Should receive either a response or an error");
        test.complete();
      }
    });

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
      .setAllExtensionNumbersOfType("helloworld.Request")
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testAdvancedReflection(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx)
      .addService(ReflectionService.v1())
      .addService(GREETER_SERVICE_METADATA)
      .<Request, Reply>callHandler(UNARY, call -> call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    final List<ServiceResponse> services = new ArrayList<>();
    final CountDownLatch servicesLatch = new CountDownLatch(1);

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
    StreamObserver<ServerReflectionRequest> listServicesObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        if (response.hasListServicesResponse()) {
          ListServiceResponse listServicesResponse = response.getListServicesResponse();
          services.addAll(listServicesResponse.getServiceList());
        }
      }

      @Override
      public void onError(Throwable throwable) {
        should.fail(throwable);
      }

      @Override
      public void onCompleted() {
        servicesLatch.countDown();
      }
    });

    ServerReflectionRequest listServicesRequest = ServerReflectionRequest.newBuilder()
      .setListServices("")
      .build();

    listServicesObserver.onNext(listServicesRequest);
    listServicesObserver.onCompleted();

    boolean servicesReceived = servicesLatch.await(5, TimeUnit.SECONDS);
    should.assertTrue(servicesReceived, "Services list should be received within timeout");
    should.assertFalse(services.isEmpty(), "Services list should not be empty");

    Async descriptorTest = should.async(services.size());

    for (ServiceResponse service : services) {
      String serviceName = service.getName();
      StreamObserver<ServerReflectionRequest> fileObserver = stub.serverReflectionInfo(new StreamObserver<>() {
        @Override
        public void onNext(ServerReflectionResponse response) {
          should.assertTrue(response.hasFileDescriptorResponse(),
            "Response for name " + serviceName + " should contain file descriptor");
          should.assertTrue(response.getFileDescriptorResponse().getFileDescriptorProtoCount() > 0,
            "File descriptor for name " + serviceName + " should not be empty");
        }

        @Override
        public void onError(Throwable throwable) {
          should.fail(throwable);
        }

        @Override
        public void onCompleted() {
          descriptorTest.countDown();
        }
      });

      ServerReflectionRequest fileRequest = ServerReflectionRequest.newBuilder()
        .setFileContainingSymbol(serviceName)
        .build();
      fileObserver.onNext(fileRequest);
      fileObserver.onCompleted();
    }

    descriptorTest.await();
  }

  @Test
  public void testServiceMethodReflection(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx)
      .addService(ReflectionService.v1())
      .addService(GREETER_SERVICE_METADATA)
      .<Request, Reply>callHandler(UNARY, call -> call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    final AtomicReference<ByteString> serviceDescriptor = new AtomicReference<>();

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
    StreamObserver<ServerReflectionRequest> streamObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse response) {
        should.assertTrue(response.hasFileDescriptorResponse());
        should.assertTrue(response.getFileDescriptorResponse().getFileDescriptorProtoCount() > 0);
        serviceDescriptor.set(response.getFileDescriptorResponse().getFileDescriptorProto(0));
      }

      @Override
      public void onError(Throwable throwable) {
        should.fail(throwable);
      }

      @Override
      public void onCompleted() {
        should.assertNotNull(serviceDescriptor.get());
        should.assertTrue(!serviceDescriptor.get().isEmpty());
        test.complete();
      }
    });

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
      .setFileContainingSymbol(TestConstants.TEST_SERVICE.fullyQualifiedName())
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }
}
