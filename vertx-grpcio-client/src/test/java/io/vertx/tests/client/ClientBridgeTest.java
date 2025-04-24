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
package io.vertx.tests.client;

import io.grpc.*;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.client.GrpcIoClientChannel;
import io.vertx.grpcio.common.impl.Utils;
import io.vertx.tests.common.grpc.*;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ClientBridgeTest extends ClientTest {

  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) throws IOException {

    super.testUnary(should, requestEncoding, responseEncoding);


    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel).withCompression(requestEncoding);
    Reply reply = stub.unary(Request.newBuilder().setName("Julien").build());
    // Todo : assert response encoding
    should.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryInterceptor(TestContext should) throws IOException {

    super.testUnary(should, "identity", "identity");

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    AtomicInteger status = new AtomicInteger();

    Channel ch = ClientInterceptors.intercept(channel, new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        should.assertEquals(0, status.getAndIncrement());
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            should.assertEquals(1, status.getAndIncrement());
            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onClose(Status st, Metadata trailers) {
                should.assertEquals(2, status.getAndIncrement());
                super.onClose(st, trailers);
              }
            }, headers);
          }
        };
      }
    });

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(ch).withCompression("identity");
    Reply reply = stub.unary(Request.newBuilder().setName("Julien").build());

    should.assertEquals(3, status.getAndIncrement());
  }

  @Override
  public void testServerStreaming(TestContext should) throws IOException {

    super.testServerStreaming(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    List<String> items = new ArrayList<>();
    stub.source(Empty.newBuilder().build()).forEachRemaining(item -> items.add(item.getMessage()));
    List<String> expected = IntStream.rangeClosed(0, NUM_ITEMS - 1).mapToObj(val -> "the-value-" + val).collect(Collectors.toList());
    should.assertEquals(expected, items);
  }

  @Override
  public void testServerStreamingBackPressure(TestContext should) throws IOException {

    super.testServerStreamingBackPressure(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Iterator<Reply> source = stub.source(Empty.newBuilder().build());
    while (true) {
      while (batchQueue.size() == 0) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
        }
      }
      int toRead = batchQueue.poll();
      if (toRead >= 0) {
        while (toRead-- > 0) {
          should.assertTrue(source.hasNext());
          Reply item = source.next();
        }
      } else {
        should.assertFalse(source.hasNext());
        break;
      }
    }
  }

  @Override
  public void testClientStreaming(TestContext should) throws Exception {

    super.testClientStreaming(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Request> items = stub.sink(new StreamObserver<>() {
      @Override
      public void onNext(Empty value) {
      }
      @Override
      public void onError(Throwable t) {
        should.fail(t);
      }
      @Override
      public void onCompleted() {
        test.complete();
      }
    });
    for (int i = 0; i < NUM_ITEMS; i++) {
      items.onNext(Request.newBuilder().setName("the-value-" + i).build());
      Thread.sleep(10);
    }
    items.onCompleted();
  }

  @Override
  public void testClientStreamingBackPressure(TestContext should) throws Exception {

    super.testClientStreamingBackPressure(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);
    Async done = should.async();
    stub.sink(new ClientResponseObserver<Reply, Empty>() {
      int batchCount = 0;
      @Override
      public void beforeStart(ClientCallStreamObserver<Reply> requestStream) {
        requestStream.setOnReadyHandler(() -> {
          if (batchCount < NUM_BATCHES) {
            int written = 0;
            while (requestStream.isReady()) {
              written++;
              requestStream.onNext(Reply.newBuilder().setMessage("the-value-" + batchCount).build());
            }
            batchCount++;
            batchQueue.add(written);
          } else {
            requestStream.onCompleted();
          }
        });
      }
      @Override
      public void onNext(Empty value) {
      }
      @Override
      public void onError(Throwable t) {
        should.fail(t);
      }
      @Override
      public void onCompleted() {
        done.complete();
      }
    });
  }

  @Override
  public void testClientStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {

    super.testClientStreamingCompletedBeforeHalfClose(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Request> items = stub.sink(new StreamObserver<Empty>() {
      @Override
      public void onNext(Empty value) {
        should.fail();
      }
      @Override
      public void onError(Throwable t) {
        should.assertEquals(StatusRuntimeException.class, t.getClass());
        StatusRuntimeException err = (StatusRuntimeException) t;
        should.assertEquals(Status.CANCELLED.getCode(), err.getStatus().getCode());
        test.complete();
      }
      @Override
      public void onCompleted() {
        should.fail();
      }
    });
    items.onNext(Request.newBuilder().setName("the-value").build());
  }

  @Override
  public void testBidiStreaming(TestContext should) throws Exception {

    super.testBidiStreaming(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    List<String> items = new ArrayList<>();
    StreamObserver<Request> writer = stub.pipe(new StreamObserver<Reply>() {
      @Override
      public void onNext(Reply item) {
        items.add(item.getMessage());
      }
      @Override
      public void onError(Throwable t) {
        should.fail(t);
      }
      @Override
      public void onCompleted() {
        test.complete();
      }
    });
    for (int i = 0; i < NUM_ITEMS; i++) {
      writer.onNext(Request.newBuilder().setName("the-value-" + i).build());
      Thread.sleep(10);
    }
    writer.onCompleted();
    test.awaitSuccess(20_000);
    List<String> expected = IntStream.rangeClosed(0, NUM_ITEMS - 1).mapToObj(val -> "the-value-" + val).collect(Collectors.toList());
    should.assertEquals(expected, items);
  }

  @Test
  public void testBidiStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {

    super.testBidiStreamingCompletedBeforeHalfClose(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Request> writer = stub.pipe(new StreamObserver<Reply>() {
      @Override
      public void onNext(Reply item) {
        should.fail();
      }
      @Override
      public void onError(Throwable t) {
        should.fail(t);
      }
      @Override
      public void onCompleted() {
        test.complete();
      }
    });
    writer.onNext(Request.newBuilder().setName("the-value").build());
  }

  @Override
  public void testStatus(TestContext should) throws IOException {

    super.testStatus(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    Request request = Request.newBuilder().setName("Julien").build();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    try {
      stub.unary(request);
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.UNAVAILABLE.getCode(), e.getStatus().getCode());
      should.assertEquals("~Greeter temporarily unavailable...~", e.getStatus().getDescription());
    }
  }

  @Override
  public void testFail(TestContext should) throws Exception {
    super.testFail(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);
    Async latch = should.async();
    StreamObserver<Request> sink = stub.pipe(new StreamObserver<Reply>() {
      int count = 0;
      @Override
      public void onNext(Reply value) {
        if (count++ == 0) {
          latch.complete();
        }
      }
      @Override
      public void onError(Throwable t) {
      }
      @Override
      public void onCompleted() {
      }
    });
    sink.onNext(Request.newBuilder().setName("item").build());
    latch.awaitSuccess(20_000);
    ((ClientCallStreamObserver<?>) sink).cancel("cancelled", new Exception());
  }

  @Override
  public void testMetadata(TestContext should) throws Exception {

    super.testMetadata(should);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    ClientInterceptor interceptor = new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            headers.put(Metadata.Key.of("custom_request_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "custom_request_header_value");
            headers.put(Metadata.Key.of("custom_request_header-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[] { 0,1,2 });
            headers.put(Metadata.Key.of("grpc-custom_request_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "grpc-custom_request_header_value");
            headers.put(Metadata.Key.of("grpc-custom_request_header-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[] { 2,1,0 });
            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onHeaders(Metadata headers) {
                should.assertEquals(3, testMetadataStep.getAndIncrement());
                should.assertEquals("custom_response_header_value", headers.get(Metadata.Key.of("custom_response_header", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 0,1,2 }, headers.get(Metadata.Key.of("custom_response_header-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                should.assertEquals("grpc-custom_response_header_value", headers.get(Metadata.Key.of("grpc-custom_response_header", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 2,1,0 }, headers.get(Metadata.Key.of("grpc-custom_response_header-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                super.onHeaders(headers);
              }
              @Override
              public void onClose(Status status, Metadata trailers) {
                should.assertEquals(4, testMetadataStep.getAndIncrement());
                should.assertEquals("custom_response_trailer_value", trailers.get(Metadata.Key.of("custom_response_trailer", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 0,1,2 }, trailers.get(Metadata.Key.of("custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                should.assertEquals("grpc-custom_response_trailer_value", trailers.get(Metadata.Key.of("grpc-custom_response_trailer", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 2,1,0 }, trailers.get(Metadata.Key.of("grpc-custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                super.onClose(status, trailers);
              }
            }, headers);
          }
        };
      }
    };

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel, interceptor));
    Request request = Request.newBuilder().setName("Julien").build();
    Reply res = stub.unary(request);
    should.assertEquals("Hello Julien", res.getMessage());

    should.assertEquals(5, testMetadataStep.get());
  }

  @Test
  public void testGrpcConnectError(TestContext should) throws Exception {

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Request request = Request.newBuilder().setName("Julien").build();
    try {
      stub.unary(request);
      fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
    }
  }

  @Test
  public void testGrpcRequestNetworkError(TestContext should) throws Exception {
    testGrpcNetworkError(should, 0);
  }

  @Test
  public void testGrpcResponseNetworkError(TestContext should) throws Exception {
    testGrpcNetworkError(should, 1);
  }

  private void testGrpcNetworkError(TestContext should, int numberOfMessages) throws Exception {

    Async listenLatch = should.async();
    NetServer proxyServer = vertx.createNetServer();
    NetClient proxyClient = vertx.createNetClient();
    proxyServer.connectHandler(inbound -> {
      inbound.pause();
      proxyClient.connect(port, "localhost")
        .onComplete(ar -> {
          inbound.resume();
          if (ar.succeeded()) {
            NetSocket outbound = ar.result();
            inbound.handler(outbound::write);
            outbound.handler(inbound::write);
            inbound.closeHandler(err -> outbound.close());
            outbound.closeHandler(err -> inbound.close());
          } else {
            inbound.close();
          }
        });
    }).listen(port + 1, "localhost").onComplete(should.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess(20_000);

    CountDownLatch latch = new CountDownLatch(1);
    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        latch.countDown();
        for (int i = 0;i < numberOfMessages;i++) {
          responseObserver.onNext(Reply.newBuilder().build());
        }
      }
    };
    startServer(called);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port + 1, "localhost"));

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    try {
      Iterator<Reply> it = stub.source(Empty.getDefaultInstance());
      latch.await(20, TimeUnit.SECONDS);
      for (int i = 0;i < numberOfMessages;i++) {
        it.next();
      }
      proxyServer.close();
      it.next();
      fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.Code.UNKNOWN, e.getStatus().getCode());
    }
  }

  @Test
  public void testGrpcResponseHttpReset(TestContext should) {
    testGrpcResponseHttpError(should, req -> {
      req.endHandler(v -> {
        req.response().reset(0x07); // REFUSED_STREAM
      });
    }, Status.Code.UNAVAILABLE);
  }

  @Test
  public void testGrpcResponseInvalidHttpCode(TestContext should) {
    testGrpcResponseHttpError(should, req -> {
      req.endHandler(v -> {
        req.response().putHeader(GrpcHeaderNames.GRPC_STATUS, "0").setStatusCode(500).end();
      });
    }, Status.Code.INTERNAL);
  }

  @Test
  public void testGrpcResponseInvalidHttpCode__(TestContext should) {
    testGrpcResponseHttpError(should, req -> {
      req.endHandler(v -> {
        req.response().putHeader(GrpcHeaderNames.GRPC_STATUS, "0").setStatusCode(500).end();
      });
    }, Status.Code.INTERNAL);
  }

  private void testGrpcResponseHttpError(TestContext should, Handler<HttpServerRequest> handler, Status.Code expectedStatus) {

    Async listenLatch = should.async();
    HttpServer server = vertx.createHttpServer();
    server
      .requestHandler(handler)
      .listen(port, "localhost")
      .onComplete(should.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess(20_000);

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Request request = Request.newBuilder().setName("Julien").build();
    try {
      stub.unary(request);
      fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(expectedStatus, e.getStatus().getCode());
    }
  }

  @Test
  public void testTimeoutOnClient(TestContext should) throws Exception {
    testTimeoutOnClient(should, stub -> {
      Request request = Request.newBuilder().setName("Julien").build();
      stub
        .withDeadlineAfter(2, TimeUnit.SECONDS)
        .unary(request);
    });
  }

  @Test
  public void testTimeoutOnClientPropagation(TestContext should) throws Exception {
    testTimeoutOnClient(should, stub -> {
      Context current = Context.current();
      Context.CancellableContext ctx = current.withDeadlineAfter(2, TimeUnit.SECONDS, Executors.newSingleThreadScheduledExecutor());
      try {
        ctx.call(() -> {
          Request request = Request.newBuilder().setName("Julien").build();
          stub
            .withDeadlineAfter(2, TimeUnit.SECONDS)
            .unary(request);
          return null;
        });
      } catch (Exception e) {
        if (e instanceof RuntimeException) {
          throw (RuntimeException)e;
        } else {
          should.fail();
        }
      }
    });
  }

  public void testTimeoutOnClient(TestContext should, Consumer<TestServiceGrpc.TestServiceBlockingStub> c) throws Exception {
    super.testTimeoutOnClient(should);
    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    try {
      c.accept(stub);
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.Code.CANCELLED, e.getStatus().getCode());
    }
  }

  @Test
  public void testTimeoutPropagationToServer(TestContext should) throws Exception {
    CompletableFuture<Long> cf = new CompletableFuture<>();
    super.testTimeoutPropagationToServer(cf);
    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    try {
      Request request = Request.newBuilder().setName("Julien").build();
      stub
        .withDeadlineAfter(2, TimeUnit.SECONDS)
        .unary(request);
    } catch (StatusRuntimeException e) {
      e.printStackTrace();
      should.assertEquals(Status.Code.CANCELLED, e.getStatus().getCode());
    }
  }

  @Test
  public void testJsonMessageFormat(TestContext should) throws Exception {

    super.testJsonMessageFormat(should, "application/grpc");

    MethodDescriptor<Request, Reply> unary =
      MethodDescriptor.newBuilder(
          Utils.<Request>marshallerFor(Request::newBuilder),
          Utils.<Reply>marshallerFor(Reply::newBuilder))
        .setFullMethodName(
          MethodDescriptor.generateFullMethodName(TestConstants.TEST_SERVICE.fullyQualifiedName(), "SayHello"))
        .setType(MethodDescriptor.MethodType.UNARY)
        .build();

    client = GrpcIoClient.client(vertx);
    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    ClientCall<Request, Reply> call = channel.newCall(unary, CallOptions.DEFAULT);
    Reply response = ClientCalls.blockingUnaryCall(call, Request.newBuilder().setName("Julien").build());
    should.assertEquals("Hello Julien", response.getMessage());
  }
}
