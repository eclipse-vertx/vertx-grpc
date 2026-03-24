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
package io.vertx.tests.client;

import io.grpc.*;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.*;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientBuilder;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClientConfigurationTest extends ClientTestBase {

  private ServerCredentials creds;
  private TrustOptions trust;
  private TestServiceGrpc.TestServiceImplBase service;

  public ClientConfigurationTest() throws IOException {
    SelfSignedCertificate cert = SelfSignedCertificate.create();
    trust = cert.trustOptions();
    creds = TlsServerCredentials
      .newBuilder()
      .keyManager(new File(cert.certificatePath()), new File(cert.privateKeyPath()))
      .build();
    service = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> plainResponseObserver) {
        ServerCallStreamObserver<Reply> responseObserver =
          (ServerCallStreamObserver<Reply>) plainResponseObserver;
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };
  }

  private GrpcClientBuilder<GrpcClient> builder() {
    return GrpcClient.builder(vertx);
  }

  @Test
  public void testH2C() throws Exception {
    startServer(service, ServerBuilder.forPort(port));
    expectCallSuccess(GrpcClient.client(vertx));
    expectCallSuccess(GrpcClient.client(vertx, new HttpClientOptions()));
    expectCallSuccess(builder().with(new HttpClientConfig()).build());
    expectCallSuccess(builder().with(new HttpClientConfig().setVersions(HttpVersion.HTTP_1_1)).build());
    expectCallSuccess(builder().with(new HttpClientConfig().setSsl(false)).with(new ClientSSLOptions().setTrustOptions(trust)).build());
  }

  @Test
  public void testH2() throws Exception {
    startServer(service, Grpc.newServerBuilderForPort(port, creds));
    expectCallSuccess(GrpcClient.client(vertx, new ClientSSLOptions().setTrustOptions(trust)));
    expectCallSuccess(GrpcClient.client(vertx, new ClientSSLOptions().setTrustAll(true)));
    expectCallSuccess(builder().with(new HttpClientConfig().setSsl(true)).with(new ClientSSLOptions().setTrustOptions(trust)).build());
    expecteCallFailure(builder().with(new HttpClientConfig().setSsl(true)).build());
  }

  public void expectCallSuccess(GrpcClient client) {
    try {
      GrpcClientRequest<Request, Reply> request = client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY).await();
      GrpcClientResponse<Request, Reply> response = request.send(Request.newBuilder().setName("Julien").build()).await();
      Reply reply = response.last().await();
      assertEquals("Hello Julien", reply.getMessage());
    } finally {
      client.close().await();
    }
  }

  public void expecteCallFailure(GrpcClient client) {
    try {
      client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY).await();
      fail();
    } catch(Exception ignore) {
    } finally {
      client.close().await();
    }
  }
}
