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

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.iogrpc.client.IoGrpcClient;
import io.vertx.iogrpc.client.IoGrpcClientChannel;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.iogrpc.server.IoGrpcServer;
import io.vertx.iogrpc.server.IoGrpcServiceBridge;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DeadlineTest extends ProxyTestBase {

  @Test
  public void testAutomaticPropagation(TestContext should) {
    Async latch = should.async(3);
    IoGrpcClient client = IoGrpcClient.client(vertx);
    Future<HttpServer> server = vertx.createHttpServer().requestHandler(IoGrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      should.assertTrue(call.timeout() > 0L);
      call.response().exceptionHandler(err -> {
        should.assertTrue(err instanceof StreamResetException);
        StreamResetException sre = (StreamResetException) err;
        should.assertEquals(8L, sre.getCode());
        latch.countDown();
      });
    })).listen(8080, "localhost");
    IoGrpcClientChannel proxyChannel = new IoGrpcClientChannel(client, SocketAddress.inetSocketAddress(8080, "localhost"));
    GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(proxyChannel);
    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        should.assertNotNull(Context.current().getDeadline());
        stub.sayHello(HelloRequest.newBuilder().setName("Julien").build(), new StreamObserver<HelloReply>() {
          @Override
          public void onNext(HelloReply helloReply) {
            should.fail();
          }
          @Override
          public void onError(Throwable throwable) {
            should.assertTrue(throwable instanceof StatusRuntimeException);
            StatusRuntimeException sre = (StatusRuntimeException) throwable;
            should.assertEquals(Status.CANCELLED, sre.getStatus());
            latch.countDown();
          }
          @Override
          public void onCompleted() {
            should.fail();
          }
        });
      }
    };
    IoGrpcServer proxy = IoGrpcServer.server(vertx);
    IoGrpcServiceBridge serverStub = IoGrpcServiceBridge.bridge(impl);
    serverStub.bind(proxy);
    HttpServer proxyServer = vertx.createHttpServer().requestHandler(proxy);
    server.flatMap(v -> proxyServer.listen(8081, "localhost")).onComplete(should.asyncAssertSuccess(v -> {
      client.request(SocketAddress.inetSocketAddress(8081, "localhost"), GreeterGrpc.getSayHelloMethod())
        .onComplete(should.asyncAssertSuccess(callRequest -> {
          callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
            should.assertEquals(GrpcStatus.DEADLINE_EXCEEDED, callResponse.status());
            latch.countDown();
          }));
          callRequest.timeout(2, TimeUnit.SECONDS).end(HelloRequest.newBuilder().setName("Julien").build());

        }));
    }));
    latch.awaitSuccess();
  }
}
