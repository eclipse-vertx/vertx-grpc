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
package io.vertx.grpc.it.tests;

import io.grpc.examples.helloworld.GreeterGrpcClient;
import io.grpc.examples.helloworld.GreeterGrpcService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.netty.handler.codec.quic.Quic;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.common.tests.GrpcTestBase;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Http3Test extends GrpcTestBase {

  @Test
  public void testUnaryCall() {
    Assume.assumeTrue("HTTP/3 requires the native QUIC transport", Quic.isAvailable());

    GrpcServer grpcServer = GrpcServer.server(vertx)
      .callHandler(GreeterGrpcService.SayHello, request -> request.handler(hello -> {
        HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + hello.getName()).build();
        request.response().end(reply);
      }));

    int port = vertx.createHttpServer(
        new HttpServerConfig().setVersions(HttpVersion.HTTP_3),
        new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get()))
      .requestHandler(grpcServer)
      .listen(0, "localhost")
      .await()
      .actualPort();

    GrpcClient client = GrpcClient.builder(vertx)
      .with(new HttpClientConfig().setVersions(HttpVersion.HTTP_3))
      .with(new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get()))
      .build();
    try {
      GreeterGrpcClient greeter = GreeterGrpcClient.create(client, SocketAddress.inetSocketAddress(port, "localhost"));
      HelloReply reply = greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await();
      assertEquals("Hello Julien", reply.getMessage());
    } finally {
      client.close().await();
    }
  }
}
