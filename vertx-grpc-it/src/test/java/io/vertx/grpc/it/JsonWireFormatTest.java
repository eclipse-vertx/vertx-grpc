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

import io.grpc.examples.helloworld.GreeterGrpcClient;
import io.grpc.examples.helloworld.GreeterGrpcService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.grpc.examples.helloworld.GreeterGrpcService.SayHello;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonWireFormatTest extends ProxyTestBase {

  @Test
  public void testUnary01() throws TimeoutException {
    GrpcClient client = GrpcClient.client(vertx);

    vertx.createHttpServer().requestHandler(GrpcServer.server(vertx).callHandler(SayHello, call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      });
    })).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    HelloReply reply = client.request(SocketAddress.inetSocketAddress(8080, "localhost"), GreeterGrpcClient.SayHello)
      .compose(callRequest -> {
        callRequest.format(WireFormat.JSON);
        return callRequest.end(HelloRequest.newBuilder().setName("Julien").build()).compose(v -> callRequest.response().compose(GrpcClientResponse::last));
      }).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testCustomJsonPrinterFlowsThroughClient() throws TimeoutException {
    GrpcClient client = GrpcClient.client(vertx);

    AtomicReference<String> serverObservedRequestBody = new AtomicReference<>();
    vertx.createHttpServer().requestHandler(GrpcServer.server(vertx).callHandler(SayHello, call -> {
      call.handler(helloRequest -> {
        // The default printer would skip the empty `name` field. With
        // alwaysPrintFieldsWithNoPresence it shows up on the wire instead.
        serverObservedRequestBody.set(helloRequest.getName());
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      });
    })).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    JsonWireFormat customFormat = WireFormat.JSON.withWriterConfig(new JsonWireFormat.WriterConfig().setAlwaysPrintFieldsWithNoPresence(true));

    HelloReply reply = client.request(SocketAddress.inetSocketAddress(8080, "localhost"), GreeterGrpcClient.SayHello)
      .compose(callRequest -> {
        callRequest.format(customFormat);
        return callRequest.end(HelloRequest.newBuilder().build()).compose(v -> callRequest.response().compose(GrpcClientResponse::last));
      })
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello ", reply.getMessage());
    assertEquals("", serverObservedRequestBody.get());
  }

  @Test
  public void testCustomJsonParserFlowsThroughServer() throws TimeoutException {
    GrpcClient client = GrpcClient.client(vertx);

    // Server uses a lenient parser to tolerate unknown fields a future client might send.
    JsonWireFormat lenientServerFormat = WireFormat.JSON.withReaderConfig(new JsonWireFormat.ReaderConfig().setIgnoringUnknownFields(true));

    vertx.createHttpServer().requestHandler(GrpcServer.server(vertx).callHandler(SayHello, call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().format(lenientServerFormat).end(helloReply);
      });
    })).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    HelloReply reply = client.request(SocketAddress.inetSocketAddress(8080, "localhost"), GreeterGrpcClient.SayHello)
      .compose(callRequest -> {
        callRequest.format(WireFormat.JSON);
        return callRequest.end(HelloRequest.newBuilder().setName("Julien").build()).compose(v -> callRequest.response().compose(GrpcClientResponse::last));
      })
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testServerOptionsJsonFormatPlumbsThrough() throws TimeoutException {
    GrpcClient client = GrpcClient.client(vertx);

    // Configure JSON server-wide with a printer that emits empty fields and a lenient parser.
    // A successful round-trip means the configuration reached both ends.
    GrpcServerOptions serverOptions = new GrpcServerOptions()
      .addEnabledFormat(WireFormat.JSON
        .withWriterConfig(new JsonWireFormat.WriterConfig().setAlwaysPrintFieldsWithNoPresence(true))
        .withReaderConfig(new JsonWireFormat.ReaderConfig().setIgnoringUnknownFields(true))
      );

    vertx.createHttpServer().requestHandler(GrpcServer.server(vertx, serverOptions).callHandler(SayHello, call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      });
    })).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    HelloReply reply = client.request(SocketAddress.inetSocketAddress(8080, "localhost"), GreeterGrpcClient.SayHello)
      .compose(callRequest -> {
        callRequest.format(WireFormat.JSON);
        return callRequest.end(HelloRequest.newBuilder().setName("Julien").build()).compose(v -> callRequest.response().compose(GrpcClientResponse::last));
      })
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnary02() throws TimeoutException {
    GrpcClient client = GrpcClient.client(vertx);

    GreeterGrpcService greeter = new GreeterGrpcService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
      }
    };

    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(GreeterGrpcService.of(greeter));

    vertx
      .createHttpServer()
      .requestHandler(grpcServer)
      .listen(8080, "localhost")
      .await(10, TimeUnit.SECONDS);

    GreeterGrpcClient stub = GreeterGrpcClient.create(client, SocketAddress.inetSocketAddress(8080, "localhost"), WireFormat.JSON);

    HelloReply reply = stub.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }
}
