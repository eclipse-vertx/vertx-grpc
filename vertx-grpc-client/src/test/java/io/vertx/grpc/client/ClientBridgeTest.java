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
package io.vertx.grpc.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.streaming.Empty;
import io.grpc.examples.streaming.Item;
import io.grpc.examples.streaming.StreamingGrpc;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ClientBridgeTest extends ClientTest {

  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) throws IOException {

    super.testUnary(should, requestEncoding, responseEncoding);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel).withCompression(requestEncoding);
    HelloReply reply = stub.sayHello(HelloRequest.newBuilder().setName("Julien").build());
    // Todo : assert response encoding
    should.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryInterceptor(TestContext should) throws IOException {

    super.testUnary(should, "identity", "identity");

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

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

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(ch).withCompression("identity");
    HelloReply reply = stub.sayHello(HelloRequest.newBuilder().setName("Julien").build());

    should.assertEquals(3, status.getAndIncrement());
  }

  @Override
  public void testServerStreaming(TestContext should) throws IOException {

    super.testServerStreaming(should);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingBlockingStub stub = StreamingGrpc.newBlockingStub(channel);
    List<String> items = new ArrayList<>();
    stub.source(Empty.newBuilder().build()).forEachRemaining(item -> items.add(item.getValue()));
    List<String> expected = IntStream.rangeClosed(0, NUM_ITEMS - 1).mapToObj(val -> "the-value-" + val).collect(Collectors.toList());
    should.assertEquals(expected, items);
  }

  @Override
  public void testServerStreamingBackPressure(TestContext should) throws IOException {

    super.testServerStreamingBackPressure(should);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingBlockingStub stub = StreamingGrpc.newBlockingStub(channel);
    Iterator<Item> source = stub.source(Empty.newBuilder().build());
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
          Item item = source.next();
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

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Item> items = stub.sink(new StreamObserver<Empty>() {
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
      items.onNext(Item.newBuilder().setValue("the-value-" + i).build());
      Thread.sleep(10);
    }
    items.onCompleted();
  }

  @Override
  public void testClientStreamingBackPressure(TestContext should) throws Exception {

    super.testClientStreamingBackPressure(should);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);
    Async done = should.async();
    stub.sink(new ClientResponseObserver<Item, Empty>() {
      int batchCount = 0;
      @Override
      public void beforeStart(ClientCallStreamObserver<Item> requestStream) {
        requestStream.setOnReadyHandler(() -> {
          if (batchCount < NUM_BATCHES) {
            int written = 0;
            while (requestStream.isReady()) {
              written++;
              requestStream.onNext(Item.newBuilder().setValue("the-value-" + batchCount).build());
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

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Item> items = stub.sink(new StreamObserver<Empty>() {
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
    items.onNext(Item.newBuilder().setValue("the-value").build());
  }

  @Override
  public void testBidiStreaming(TestContext should) throws Exception {

    super.testBidiStreaming(should);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);

    Async test = should.async();
    List<String> items = new ArrayList<>();
    StreamObserver<Item> writer = stub.pipe(new StreamObserver<Item>() {
      @Override
      public void onNext(Item item) {
        items.add(item.getValue());
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
      writer.onNext(Item.newBuilder().setValue("the-value-" + i).build());
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

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Item> writer = stub.pipe(new StreamObserver<Item>() {
      @Override
      public void onNext(Item item) {
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
    writer.onNext(Item.newBuilder().setValue("the-value").build());
  }

  @Override
  public void testStatus(TestContext should) throws IOException {

    super.testStatus(should);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    HelloRequest request = HelloRequest.newBuilder().setName("Julien").build();
    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
    try {
      stub.sayHello(request);
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.UNAVAILABLE, e.getStatus());
    }
  }

  @Override
  public void testFail(TestContext should) throws Exception {
    super.testFail(should);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);
    Async latch = should.async();
    StreamObserver<Item> sink = stub.pipe(new StreamObserver<Item>() {
      int count = 0;
      @Override
      public void onNext(Item value) {
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
    sink.onNext(Item.newBuilder().setValue("item").build());
    latch.awaitSuccess(20_000);
    ((ClientCallStreamObserver<Item>) sink).cancel("cancelled", new Exception());
  }

  @Override
  public void testMetadata(TestContext should) throws Exception {

    super.testMetadata(should);

    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));

    ClientInterceptor interceptor = new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            headers.put(Metadata.Key.of("custom_request_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "custom_request_header_value");
            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onHeaders(Metadata headers) {
                should.assertEquals(3, testMetadataStep.getAndIncrement());
                should.assertEquals("custom_response_header_value", headers.get(Metadata.Key.of("custom_response_header", Metadata.ASCII_STRING_MARSHALLER)));
                super.onHeaders(headers);
              }
              @Override
              public void onClose(Status status, Metadata trailers) {
                should.assertEquals(4, testMetadataStep.getAndIncrement());
                should.assertEquals("custom_response_trailer_value", trailers.get(Metadata.Key.of("custom_response_trailer", io.grpc.Metadata.ASCII_STRING_MARSHALLER)));
                super.onClose(status, trailers);
              }
            }, headers);
          }
        };
      }
    };

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(ClientInterceptors.intercept(channel, interceptor));
    HelloRequest request = HelloRequest.newBuilder().setName("Julien").build();
    HelloReply res = stub.sayHello(request);
    should.assertEquals("Hello Julien", res.getMessage());

    should.assertEquals(5, testMetadataStep.get());
  }
}
