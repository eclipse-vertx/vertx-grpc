/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.it;

import com.google.protobuf.ByteString;
import io.grpc.examples.helloworld.*;
import io.grpc.testing.integration.*;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.client.GrpcClient;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ProtocPluginTest extends ProxyTestBase {

  @Test
  public void testHelloWorld(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    GreeterClient client = GreeterGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.sayHello(HelloRequest.newBuilder()
        .setName("World")
        .build())
      .onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals("Hello World", reply.getMessage());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testUnary_PromiseArg(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public void unaryCall(Messages.SimpleRequest request, Completable<Messages.SimpleResponse> response) {
        response.succeed(Messages.SimpleResponse.newBuilder()
          .setUsername("FooBar")
          .build());
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.unaryCall(Messages.SimpleRequest.newBuilder()
        .setFillUsername(true)
        .build())
      .onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals("FooBar", reply.getUsername());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testUnary_FutureReturn(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public Future<Messages.SimpleResponse> unaryCall(Messages.SimpleRequest request) {
        return Future.succeededFuture(Messages.SimpleResponse.newBuilder()
          .setUsername("FooBar")
          .build());
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.unaryCall(Messages.SimpleRequest.newBuilder()
        .setFillUsername(true)
        .build())
      .onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals("FooBar", reply.getUsername());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testUnary_FutureReturn_ErrorHandling(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public Future<Messages.SimpleResponse> unaryCall(Messages.SimpleRequest request) {
        throw new RuntimeException("Simulated error");
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.unaryCall(Messages.SimpleRequest.newBuilder()
        .setFillUsername(true)
        .build())
      .onComplete(should.asyncAssertFailure(err -> {
        should.assertTrue(err instanceof InvalidStatusException);
        InvalidStatusException ise = (InvalidStatusException) err;
        should.assertEquals(GrpcStatus.INTERNAL, ise.actualStatus());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testUnary_FutureReturn_MethodSignature(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      protected void sayHelloDouble(HelloDoubleRequest request, Completable<HelloReply> response) {
        response.succeed(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer).listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    GreeterGrpcClient client = GreeterGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.sayHelloDouble("World", 25).onComplete(should.asyncAssertSuccess(reply -> {
      should.assertEquals("Hello World", reply.getMessage());
      test.complete();
    }));

    test.awaitSuccess();
  }

  @Test
  public void testManyUnary_PromiseArg(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public void streamingInputCall(ReadStream<Messages.StreamingInputCallRequest> request, Completable<Messages.StreamingInputCallResponse> response) {
        List<Messages.StreamingInputCallRequest> list = new ArrayList<>();
        request.handler(list::add);
        request.endHandler($ -> {
          Messages.StreamingInputCallResponse resp = Messages.StreamingInputCallResponse.newBuilder()
            .setAggregatedPayloadSize(list.size())
            .build();
          response.succeed(resp);
        });
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.streamingInputCall((req, err) -> {
        req.write(Messages.StreamingInputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingInputRequest-1", StandardCharsets.UTF_8)).build())
          .build());
        req.write(Messages.StreamingInputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingInputRequest-2", StandardCharsets.UTF_8)).build())
          .build());
        req.end();
      })
      .onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals(2, reply.getAggregatedPayloadSize());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testManyUnary_FutureReturn(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public Future<Messages.StreamingInputCallResponse> streamingInputCall(ReadStream<Messages.StreamingInputCallRequest> request) {
        Promise<Messages.StreamingInputCallResponse> promise = Promise.promise();
        List<Messages.StreamingInputCallRequest> list = new ArrayList<>();
        request.handler(list::add);
        request.endHandler($ -> {
          Messages.StreamingInputCallResponse resp = Messages.StreamingInputCallResponse.newBuilder()
            .setAggregatedPayloadSize(list.size())
            .build();
          promise.complete(resp);
        });
        return promise.future();
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.streamingInputCall((req, err) -> {
        req.write(Messages.StreamingInputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingInputRequest-1", StandardCharsets.UTF_8)).build())
          .build());
        req.write(Messages.StreamingInputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingInputRequest-2", StandardCharsets.UTF_8)).build())
          .build());
        req.end();
      })
      .onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals(2, reply.getAggregatedPayloadSize());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testManyUnary_FutureReturn_ErrorHandling(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public Future<Messages.StreamingInputCallResponse> streamingInputCall(ReadStream<Messages.StreamingInputCallRequest> request) {
        throw new RuntimeException("Simulated error");
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.streamingInputCall((req, err) -> {
        req.write(Messages.StreamingInputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingInputRequest-1", StandardCharsets.UTF_8)).build())
          .build());
        req.write(Messages.StreamingInputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingInputRequest-2", StandardCharsets.UTF_8)).build())
          .build());
        req.end();
      })
      .onComplete(should.asyncAssertFailure(err -> {
        should.assertTrue(err instanceof InvalidStatusException);
        InvalidStatusException ise = (InvalidStatusException) err;
        should.assertEquals(GrpcStatus.INTERNAL, ise.actualStatus());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testUnaryMany_WriteStreamArg(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public void streamingOutputCall(Messages.StreamingOutputCallRequest request, WriteStream<Messages.StreamingOutputCallResponse> response) {
        response.write(Messages.StreamingOutputCallResponse.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-1", StandardCharsets.UTF_8)).build())
          .build());
        response.write(Messages.StreamingOutputCallResponse.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-2", StandardCharsets.UTF_8)).build())
          .build());
        response.end();
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    Messages.StreamingOutputCallRequest request = Messages.StreamingOutputCallRequest.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest", StandardCharsets.UTF_8)).build())
      .build();

    client.streamingOutputCall(request)
      .onComplete(should.asyncAssertSuccess(response -> {
        List<Messages.StreamingOutputCallResponse> list = new ArrayList<>();
        response.handler(list::add);
        response.endHandler($ -> {
          should.assertEquals(2, list.size());
          test.complete();
        });
      }));
    test.awaitSuccess();
  }

  @Test
  public void testUnaryMany_ReadStreamReturn(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      protected void streamingOutputCall(Messages.StreamingOutputCallRequest request, WriteStream<Messages.StreamingOutputCallResponse> response) {
        response.write(Messages.StreamingOutputCallResponse.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-1", StandardCharsets.UTF_8)).build())
          .build());
        response.write(Messages.StreamingOutputCallResponse.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-2", StandardCharsets.UTF_8)).build())
          .build());
        response.end();
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    Messages.StreamingOutputCallRequest request = Messages.StreamingOutputCallRequest.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest", StandardCharsets.UTF_8)).build())
      .build();
    client.streamingOutputCall(request)
      .onComplete(should.asyncAssertSuccess(response -> {
        List<Messages.StreamingOutputCallResponse> list = new ArrayList<>();
        response.handler(list::add);
        response.endHandler($ -> {
          should.assertEquals(2, list.size());
          test.complete();
        });
        response.exceptionHandler(should::fail);
      }));
    test.awaitSuccess();
  }

  @Test
  public void testUnaryMany_ReadStreamReturn_ErrorHandling(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      protected void streamingOutputCall(Messages.StreamingOutputCallRequest request, WriteStream<Messages.StreamingOutputCallResponse> response) {
        throw new RuntimeException("Simulated error");
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    Messages.StreamingOutputCallRequest request = Messages.StreamingOutputCallRequest.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest", StandardCharsets.UTF_8)).build())
      .build();
    client.streamingOutputCall(request)
      .onComplete(should.asyncAssertFailure(err -> {
        should.assertEquals("Invalid status: actual:INTERNAL, expected:OK", err.getMessage());
        test.complete();
      }));
    test.awaitSuccess();
  }

  @Test
  public void testmanyMany_WriteStreamArg(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      public void fullDuplexCall(ReadStream<Messages.StreamingOutputCallRequest> request, WriteStream<Messages.StreamingOutputCallResponse> response) {
        request.endHandler($ -> {
          response.write(Messages.StreamingOutputCallResponse.newBuilder()
            .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-1", StandardCharsets.UTF_8)).build())
            .build());
          response.write(Messages.StreamingOutputCallResponse.newBuilder()
            .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-2", StandardCharsets.UTF_8)).build())
            .build());
          response.end();
        });
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.fullDuplexCall((req, err) -> {
        req.write(Messages.StreamingOutputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest-1", StandardCharsets.UTF_8)).build())
          .build());
        req.write(Messages.StreamingOutputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest-2", StandardCharsets.UTF_8)).build())
          .build());
        req.end();
      })
      .onComplete(should.asyncAssertSuccess(response -> {
        List<Messages.StreamingOutputCallResponse> list = new ArrayList<>();
        response.handler(list::add);
        response.endHandler($ -> {
          should.assertEquals(2, list.size());
          test.complete();
        });
        response.exceptionHandler(should::fail);
      }));
    test.awaitSuccess();
  }

  @Test
  public void testmanyMany_ReadStreamReturn(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      protected void fullDuplexCall(ReadStream<Messages.StreamingOutputCallRequest> request, WriteStream<Messages.StreamingOutputCallResponse> response) {
        request.endHandler($ -> {
          response.write(Messages.StreamingOutputCallResponse.newBuilder()
            .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-1", StandardCharsets.UTF_8)).build())
            .build());
          response.write(Messages.StreamingOutputCallResponse.newBuilder()
            .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputResponse-2", StandardCharsets.UTF_8)).build())
            .build());
          response.end();
        });
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.fullDuplexCall((req, err) -> {
        req.write(Messages.StreamingOutputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest-1", StandardCharsets.UTF_8)).build())
          .build());
        req.write(Messages.StreamingOutputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest-2", StandardCharsets.UTF_8)).build())
          .build());
        req.end();
      })
      .onComplete(should.asyncAssertSuccess(response -> {
        List<Messages.StreamingOutputCallResponse> list = new ArrayList<>();
        response.handler(list::add);
        response.endHandler($ -> {
          should.assertEquals(2, list.size());
          test.complete();
        });
        response.exceptionHandler(should::fail);
      }));
    test.awaitSuccess();
  }

  @Test
  public void testmanyMany_ReadStreamReturn_ErrorHandling(TestContext should) throws Exception {
    // Create gRPC Server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
      @Override
      protected void fullDuplexCall(ReadStream<Messages.StreamingOutputCallRequest> request, WriteStream<Messages.StreamingOutputCallResponse> response) {
        throw new RuntimeException("Simulated error");
      }
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.fullDuplexCall((req, err) -> {
        req.write(Messages.StreamingOutputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest-1", StandardCharsets.UTF_8)).build())
          .build());
        req.write(Messages.StreamingOutputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom("StreamingOutputRequest-2", StandardCharsets.UTF_8)).build())
          .build());
        req.end();
      })
      .onComplete(should.asyncAssertFailure(err -> {
        should.assertEquals("Invalid status: actual:INTERNAL, expected:OK", err.getMessage());
        test.complete();
      }));
    test.awaitSuccess();

    httpServer.close();
  }

  @Test
  public void testUnimplementedService() throws Exception {
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(TestServiceGrpcService.of(new TestServiceService() {
    }));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).await(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcClient grpcClient = GrpcClient.client(vertx);
    TestServiceClient client = TestServiceGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    try {
      client.unaryCall(Messages.SimpleRequest.newBuilder()
          .setFillUsername(true)
          .build()).await(20, TimeUnit.SECONDS);
    } catch (InvalidStatusException expected) {
      assertEquals(GrpcStatus.UNIMPLEMENTED, expected.actualStatus());
    }

    try {
      client.streamingInputCall((req, err) -> {
        req.write(Messages.StreamingInputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFromUtf8("blah"))).build());
      }).await(20, TimeUnit.SECONDS);
    } catch (InvalidStatusException expected) {
      assertEquals(GrpcStatus.UNIMPLEMENTED, expected.actualStatus());
    }

    try {
      client.fullDuplexCall((req, err) -> {
        req.write(Messages.StreamingOutputCallRequest.newBuilder()
          .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFromUtf8("blah"))).build());
      }).await(20, TimeUnit.SECONDS);
    } catch (InvalidStatusException expected) {
      assertEquals(GrpcStatus.UNIMPLEMENTED, expected.actualStatus());
    }

    try {
      client.streamingOutputCall(Messages.StreamingOutputCallRequest.newBuilder()
        .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFromUtf8("blah")).build())
        .build()).await(20, TimeUnit.SECONDS);
    } catch (InvalidStatusException expected) {
      assertEquals(GrpcStatus.UNIMPLEMENTED, expected.actualStatus());
    }
  }
}
