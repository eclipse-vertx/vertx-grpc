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

import io.grpc.examples.helloworld.*;
import io.grpc.testing.integration.*;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientCompressionOptions;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.Service;
import org.junit.Test;

public class CompressionTest extends ProtocPluginTestBase {

  @Test
  public void testIdentityCompression(TestContext should) throws Exception {
    testCompression(should, "identity");
  }

  @Test
  public void testGzipCompression(TestContext should) throws Exception {
    testCompression(should, "gzip");
  }

  @Test
  public void testSnappyCompression(TestContext should) throws Exception {
    testCompression(should, "snappy");
  }

  @Test
  public void testInvalidCompression(TestContext should) throws Exception {
    // Create gRPC Server with invalid compression
    GrpcServer grpcServer = GrpcServer.server(vertx, new GrpcServerOptions());

    grpcServer.addService(greeterService(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    }));

    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer).listen(8080).toCompletionStage().toCompletableFuture().get(20, java.util.concurrent.TimeUnit.SECONDS);

    // Create gRPC Client with invalid compression
    GrpcClient grpcClient = GrpcClient.client(vertx, new GrpcClientOptions().setCompression(new GrpcClientCompressionOptions().setCompressionAlgorithm("invalid")));
    GreeterClient client = greeterClient(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.sayHello(HelloRequest.newBuilder().setName("World").build())
      .onFailure(exception -> test.complete())
      .onSuccess(reply -> should.fail("Expected failure due to invalid compression"));

    test.awaitSuccess(20_000);

    // Close the server
    httpServer.close();
  }

  private void testCompression(TestContext should, String compressionType) throws Exception {
    // Create gRPC Server with the specified compression
    GrpcServer grpcServer = GrpcServer.server(vertx);

    grpcServer.addService(greeterService(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    }));

    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer).listen(8080).toCompletionStage().toCompletableFuture().get(20, java.util.concurrent.TimeUnit.SECONDS);

    // Create gRPC Client with the specified compression
    GrpcClient grpcClient = GrpcClient.client(vertx, new GrpcClientOptions().setCompression(new GrpcClientCompressionOptions().setCompressionAlgorithm(compressionType)));
    GreeterClient client = greeterClient(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));

    Async test = should.async();
    client.sayHello(HelloRequest.newBuilder()
        .setName("World")
        .build())
      .onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals("Hello World", reply.getMessage());
        test.complete();
      }));
    test.awaitSuccess(20_000);

    // Close the server
    httpServer.close();
  }

  @Override
  protected GrpcServer grpcServer() {
    return GrpcServer.server(vertx);
  }

  @Override
  protected GrpcClient grpcClient() {
    return GrpcClient.client(vertx);
  }

  @Override
  protected Service greeterService(GreeterService service) {
    return GreeterGrpcService.of(service);
  }

  @Override
  protected GreeterClient greeterClient(GrpcClient grpcClient, SocketAddress socketAddress) {
    return GreeterGrpcClient.create(grpcClient, socketAddress);
  }

  @Override
  protected Service testService(TestServiceService service) {
    return TestServiceGrpcService.of(service);
  }

  @Override
  protected TestServiceClient testClient(GrpcClient client, SocketAddress socketAddress) {
    return TestServiceGrpcClient.create(client, socketAddress);
  }
}
