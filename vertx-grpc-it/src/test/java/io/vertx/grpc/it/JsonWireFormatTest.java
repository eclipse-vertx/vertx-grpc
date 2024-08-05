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

import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.VertxGreeterGrpcClient;
import io.grpc.examples.helloworld.VertxGreeterGrpcServer;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcServer;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonWireFormatTest extends ProxyTestBase {

  @Test
  public void testUnary01(TestContext should) {

    GrpcClient client = GrpcClient.client(vertx);

    Future<HttpServer> server = vertx.createHttpServer().requestHandler(GrpcServer.server(vertx).callHandler(VertxGreeterGrpcServer.SayHello_JSON, call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      });
    })).listen(8080, "localhost");

    Async test = should.async();
    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(SocketAddress.inetSocketAddress(8080, "localhost"), VertxGreeterGrpcClient.SayHello_JSON)
        .onComplete(should.asyncAssertSuccess(callRequest -> {
          callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
            AtomicInteger count = new AtomicInteger();
            callResponse.handler(reply -> {
              should.assertEquals(1, count.incrementAndGet());
              should.assertEquals("Hello Julien", reply.getMessage());
            });
            callResponse.endHandler(v2 -> {
              should.assertEquals(1, count.get());
              test.complete();
            });
          }));
          callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
        }));
    }));

    test.awaitSuccess(20_000);
  }

  @Test
  public void testUnary02(TestContext should) {

    GrpcClient client = GrpcClient.client(vertx);

    VertxGreeterGrpcServer.GreeterApi greeter = new VertxGreeterGrpcServer.GreeterApi() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
      }
    };

    GrpcServer grpcServer = GrpcServer.server(vertx);

    greeter.bind_sayHello(grpcServer, WireFormat.JSON);

    Future<HttpServer> server = vertx
      .createHttpServer()
      .requestHandler(grpcServer)
      .listen(8080, "localhost");

    VertxGreeterGrpcClient stub = new VertxGreeterGrpcClient(client, SocketAddress.inetSocketAddress(8080, "localhost"), WireFormat.JSON);

    Async test = should.async();
    server.onComplete(should.asyncAssertSuccess(v -> {
      stub.sayHello(HelloRequest.newBuilder().setName("Julien").build()).onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals("Hello Julien", reply.getMessage());
        test.complete();
      }));
    }));

    test.awaitSuccess(20_000);
  }
}
