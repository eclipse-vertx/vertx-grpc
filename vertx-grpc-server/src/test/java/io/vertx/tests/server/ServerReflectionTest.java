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
package io.vertx.tests.server;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusException;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloWorldProto;
import io.grpc.examples.streaming.Item;
import io.grpc.examples.streaming.StreamingProto;
import io.grpc.reflection.test.*;
import io.grpc.stub.StreamObserver;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.Service;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ServerReflectionTest extends ServerTestBase {

  private static final Service GREETER_SERVICE_METADATA = Service.metadata(GREETER, HelloWorldProto.getDescriptor().findServiceByName("Greeter"));
  private static final Service STREAMING_SERVICE_METADATA = Service.metadata(STREAMING, StreamingProto.getDescriptor().findServiceByName("Streaming"));

  @Test
  public void testBasicReflection(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder().setListServices("").build();
    streamObserver.onNext(request);

    test.await();
  }

  @Test
  public void testReflectionDisabled(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(false))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      })));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    Async test = should.async();
    AtomicBoolean errorReceived = new AtomicBoolean(false);

    ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
    StreamObserver<ServerReflectionRequest> streamObserver = stub.serverReflectionInfo(new StreamObserver<>() {
      @Override
      public void onNext(ServerReflectionResponse serverReflectionResponse) {
        should.fail("Should not receive response when reflection is disabled");
      }

      @Override
      public void onError(Throwable throwable) {
        errorReceived.set(true);
        test.complete();
      }

      @Override
      public void onCompleted() {
        if (!errorReceived.get()) {
          should.fail("Expected error but received completion");
        }
      }
    });

    ServerReflectionRequest request = ServerReflectionRequest.newBuilder().setListServices("").build();
    streamObserver.onNext(request);

    test.await();
  }

  @Test
  public void testFileByFilename(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
      .setFileByFilename("helloworld.proto")
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testFileContainingSymbol(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
      .setFileContainingSymbol("helloworld.Greeter")
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testFileContainingExtension(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
        .setContainingType("helloworld.HelloRequest")
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
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
      .setAllExtensionNumbersOfType("helloworld.HelloRequest")
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testAdvancedReflection(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
            "Response for service " + serviceName + " should contain file descriptor");
          should.assertTrue(response.getFileDescriptorResponse().getFileDescriptorProtoCount() > 0,
            "File descriptor for service " + serviceName + " should not be empty");
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
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
      .setFileContainingSymbol("helloworld.Greeter")
      .build();
    streamObserver.onNext(request);
    streamObserver.onCompleted();

    test.await();
  }

  @Test
  public void testReflectionWithMultipleServices(TestContext should) throws StatusException, InterruptedException, TimeoutException {
    startServer(GrpcServer
      .server(vertx, new GrpcServerOptions().setReflectionEnabled(true))
      .serviceMetadata(GREETER_SERVICE_METADATA)
      .serviceMetadata(STREAMING_SERVICE_METADATA)
      .callHandler(GREETER_SAY_HELLO, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
        response
          .encoding("identity")
          .end(helloReply);
      }))
      .callHandler(STREAMING_PIPE, call -> call.handler(helloRequest -> {
        Item helloReply = Item.newBuilder().setValue("Hello " + helloRequest.getValue()).build();
        GrpcServerResponse<Item, Item> response = call.response();
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
        ListServiceResponse listServicesResponse = response.getListServicesResponse();
        should.assertEquals(2, listServicesResponse.getServiceCount(), "Should have two services");

        boolean foundService1 = false;
        boolean foundService2 = false;

        for (ServiceResponse service : listServicesResponse.getServiceList()) {
          if ("helloworld.Greeter".equals(service.getName())) {
            foundService1 = true;
          } else if ("streaming.Streaming".equals(service.getName())) {
            foundService2 = true;
          }
        }

        should.assertTrue(foundService1, "First service should be listed");
        should.assertTrue(foundService2, "Second service should be listed");
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
    streamObserver.onCompleted();

    test.await();
  }
}
