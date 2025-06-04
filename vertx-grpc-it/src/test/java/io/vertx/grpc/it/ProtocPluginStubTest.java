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

import io.grpc.*;
import io.grpc.examples.helloworld.*;
import io.grpc.testing.integration.*;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.client.GrpcIoClientChannel;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class ProtocPluginStubTest extends ProtocPluginTestBase {

  @Override
  protected GrpcIoServer grpcServer() {
    return GrpcIoServer.server(vertx);
  }

  @Override
  protected GrpcIoClient grpcClient() {
    return GrpcIoClient.client(vertx);
  }

  @Override
  protected Service greeterService(GreeterService service) {
    return GrpcIoServiceBridge.bridge(GreeterGrpcIo.bindableServiceOf(service));
  }

  @Override
  protected GreeterClient greeterClient(GrpcClient grpcClient, SocketAddress socketAddress) {
    return GreeterGrpcIo.newStub((GrpcIoClient)grpcClient, socketAddress);
  }

  @Override
  protected Service testService(TestServiceService service) {
    return GrpcIoServiceBridge.bridge(TestServiceGrpcIo.bindableServiceOf(service));
  }

  @Override
  protected TestServiceClient testClient(GrpcClient grpcClient, SocketAddress socketAddress) {
    return TestServiceGrpcIo.newStub((GrpcIoClient) grpcClient, socketAddress);
  }

  @Test
  public void testInterceptors(TestContext should) throws Exception {

    // Create gRPC Server
    GrpcIoServer grpcServer = grpcServer();

    BindableService serviceServiceDef = GreeterGrpcIo.bindableServiceOf(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    });

    AtomicBoolean serverInterception = new AtomicBoolean();
    ServerServiceDefinition interceptedDef = ServerInterceptors.intercept(serviceServiceDef, new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
          @Override
          public void onHalfClose() {
            serverInterception.set(true);
            super.onHalfClose();
          }
        };
      }
    });

    grpcServer.addService(GrpcIoServiceBridge.bridge(interceptedDef));
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.requestHandler(grpcServer)
      .listen(8080).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);

    // Create gRPC Client
    GrpcIoClient grpcClient = grpcClient();

    AtomicBoolean clientInterception = new AtomicBoolean();
    ClientInterceptor clientInterceptor = new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
          @Override
          public void halfClose() {
            clientInterception.set(true);
            super.halfClose();
          }
        };
      }
    };

    GrpcIoClientChannel channel = new GrpcIoClientChannel(grpcClient, SocketAddress.inetSocketAddress(port, "localhost"));
    for (int i = 0;i < 2;i++) {
      GreeterClient client;
      if (i == 0) {
        client = GreeterGrpcIo.newStub(vertx, ClientInterceptors.intercept(new GrpcIoClientChannel(grpcClient, SocketAddress.inetSocketAddress(port, "localhost")), clientInterceptor));
      } else {
        client = GreeterGrpcIo.newStub(vertx, channel).withInterceptors(clientInterceptor);
      }
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


    assertTrue(serverInterception.get());
    assertTrue(clientInterception.get());
  }
}
