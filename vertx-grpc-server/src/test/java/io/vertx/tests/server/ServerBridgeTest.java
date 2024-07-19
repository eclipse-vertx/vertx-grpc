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
package io.vertx.tests.server;

import io.grpc.*;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.streaming.Empty;
import io.grpc.examples.streaming.Item;
import io.grpc.examples.streaming.StreamingGrpc;
import io.grpc.protobuf.StatusProto;
import io.grpc.examples.streaming.StreamingGrpc.StreamingImplBase;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServiceBridge;
import org.junit.Ignore;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerBridgeTest extends ServerTest {

  @Override
  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) {
    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        if (!responseEncoding.equals("identity")) {
          ((ServerCallStreamObserver<?>)responseObserver).setCompression("gzip");
        }
        if (!requestEncoding.equals("identity")) {
          // No way to check the request encoding with the API
        }
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testUnary(should, requestEncoding, responseEncoding);
  }

  @Test
  public void testUnaryInterceptor(TestContext should) {

    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    Async done = should.async();
    AtomicInteger count = new AtomicInteger();
    ServerServiceDefinition def = ServerInterceptors.intercept(impl, new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        should.assertEquals(0, count.getAndIncrement());
        call = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
          @Override
          public void sendHeaders(Metadata headers) {
            should.assertEquals(4, count.getAndIncrement());
            super.sendHeaders(headers);
          }
          @Override
          public void sendMessage(RespT message) {
            should.assertEquals(5, count.getAndIncrement());
            super.sendMessage(message);
          }
          @Override
          public void close(Status status, Metadata trailers) {
            should.assertEquals(6, count.getAndIncrement());
            super.close(status, trailers);
          }
        };
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(next.startCall(call, headers)) {
          @Override
          public void onReady() {
            should.assertEquals(1, count.getAndIncrement());
            super.onReady();
          }
          @Override
          public void onMessage(ReqT message) {
            should.assertEquals(2, count.getAndIncrement());
            super.onMessage(message);
          }
          @Override
          public void onHalfClose() {
            should.assertEquals(3, count.getAndIncrement());
            super.onHalfClose();
          }
          @Override
          public void onComplete() {
            should.assertEquals(7, count.getAndIncrement());
            super.onComplete();
            done.complete();
          }
        };
      }
    });

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(def);
    serverStub.bind(server);
    startServer(server);

    super.testUnary(should, "identity", "identity");
  }

  @Override
  public void testStatus(TestContext should) {

    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE));
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testStatus(should);
  }

  @Override
  public void testServerStreaming(TestContext should) {

    StreamingGrpc.StreamingImplBase impl = new StreamingGrpc.StreamingImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Item> responseObserver) {
        for (int i = 0; i < NUM_ITEMS; i++) {
          Item item = Item.newBuilder().setValue("the-value-" + i).build();
          responseObserver.onNext(item);
        }
        responseObserver.onCompleted();
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testServerStreaming(should);
  }

  @Override
  public void testClientStreaming(TestContext should) throws Exception {

    StreamingGrpc.StreamingImplBase impl = new StreamingGrpc.StreamingImplBase() {
      @Override
      public StreamObserver<Item> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<Item>() {
          int seq = 0;
          @Override
          public void onNext(Item value) {
            should.assertEquals(value.getValue(), "the-value-" + seq++);
          }
          @Override
          public void onError(Throwable t) {

          }
          @Override
          public void onCompleted() {
            should.assertEquals(NUM_ITEMS, seq);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
          }
        };
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testClientStreaming(should);
  }

  @Override
  public void testClientStreamingCompletedBeforeHalfClose(TestContext should) {

    StreamingGrpc.StreamingImplBase impl = new StreamingGrpc.StreamingImplBase() {
      @Override
      public StreamObserver<Item> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<Item>() {
          @Override
          public void onNext(Item value) {
            responseObserver.onCompleted();
          }
          @Override
          public void onError(Throwable t) {
            should.fail();
          }
          @Override
          public void onCompleted() {
            should.fail();
          }
        };
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testClientStreamingCompletedBeforeHalfClose(should);

  }

  @Override
  public void testBidiStreaming(TestContext should) throws Exception {

    StreamingGrpc.StreamingImplBase impl = new StreamingGrpc.StreamingImplBase() {
      @Override
      public StreamObserver<Item> pipe(StreamObserver<Item> responseObserver) {
        return new StreamObserver<Item>() {
          @Override
          public void onNext(Item value) {
            responseObserver.onNext(value);
          }
          @Override
          public void onError(Throwable t) {

          }
          @Override
          public void onCompleted() {
            responseObserver.onCompleted();
          }
        };
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testBidiStreaming(should);
  }

  @Override
  public void testBidiStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {

    StreamingGrpc.StreamingImplBase impl = new StreamingGrpc.StreamingImplBase() {
      @Override
      public StreamObserver<Item> pipe(StreamObserver<Item> responseObserver) {
        return new StreamObserver<Item>() {
          @Override
          public void onNext(Item value) {
            responseObserver.onCompleted();
          }
          @Override
          public void onError(Throwable t) {
            // should.fail(t);
          }
          @Override
          public void onCompleted() {
            // should.fail();
          }
        };
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testBidiStreamingCompletedBeforeHalfClose(should);
  }

  @Override
  public void testMetadata(TestContext should) {

    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    ServerInterceptor interceptor = new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        should.assertEquals("custom_request_header_value", headers.get(Metadata.Key.of("custom_request_header", Metadata.ASCII_STRING_MARSHALLER)));
        assertEquals(should, new byte[] { 0,1,2 }, headers.get(Metadata.Key.of("custom_request_header-bin", Metadata.BINARY_BYTE_MARSHALLER)));
        should.assertEquals("grpc-custom_request_header_value", headers.get(Metadata.Key.of("grpc-custom_request_header", Metadata.ASCII_STRING_MARSHALLER)));
        assertEquals(should, new byte[] { 2,1,0 }, headers.get(Metadata.Key.of("grpc-custom_request_header-bin", Metadata.BINARY_BYTE_MARSHALLER)));

        should.assertEquals(0, testMetadataStep.getAndIncrement());
        return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
          @Override
          public void sendHeaders(Metadata headers) {
            headers.put(Metadata.Key.of("custom_response_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "custom_response_header_value");
            headers.put(Metadata.Key.of("custom_response_header-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[]{0,1,2});
            headers.put(Metadata.Key.of("grpc-custom_response_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "grpc-custom_response_header_value");
            headers.put(Metadata.Key.of("grpc-custom_response_header-bin", io.grpc.Metadata.BINARY_BYTE_MARSHALLER), new byte[]{2,1,0});
            should.assertEquals(1, testMetadataStep.getAndIncrement());
            super.sendHeaders(headers);
          }
          @Override
          public void close(Status status, Metadata trailers) {
            trailers.put(Metadata.Key.of("custom_response_trailer", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "custom_response_trailer_value");
            trailers.put(Metadata.Key.of("custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[]{0,1,2});
            trailers.put(Metadata.Key.of("grpc-custom_response_trailer", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "grpc-custom_response_trailer_value");
            trailers.put(Metadata.Key.of("grpc-custom_response_trailer-bin", io.grpc.Metadata.BINARY_BYTE_MARSHALLER), new byte[]{2,1,0});
            int step = testMetadataStep.getAndIncrement();
            should.assertTrue(step == 2 || step == 3, "Was expected " + step + " 3 or " + step + " == 4");
            super.close(status, trailers);
          }
        },headers);
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(ServerInterceptors.intercept(impl, interceptor));
    serverStub.bind(server);
    startServer(server);

    super.testMetadata(should);
  }

  // Cannot compile because of https://github.com/protocolbuffers/protobuf/issues/16452
  // see also https://github.com/protocolbuffers/protobuf/issues/17247
  // see also https://github.com/grpc/grpc-java/issues/11015
  // waiting for https://github.com/protocolbuffers/protobuf/commit/4d8be99f2bef888921fee52e400a27e72593e1b2 to be released
  @Ignore
  @Override
  public void testTrailersOnly(TestContext should) {
  }

/*
  @Override
  public void testTrailersOnly(TestContext should) {

    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        Metadata md = new Metadata();
        md.put(Metadata.Key.of("custom_response_trailer", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "custom_response_trailer_value");
        md.put(Metadata.Key.of("custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[]{0,1,2});
        md.put(Metadata.Key.of("grpc-custom_response_trailer", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "grpc-custom_response_trailer_value");
        md.put(Metadata.Key.of("grpc-custom_response_trailer-bin", io.grpc.Metadata.BINARY_BYTE_MARSHALLER), new byte[]{2,1,0});
        final StatusRuntimeException t =
          StatusProto.toStatusRuntimeException(
            com.google.rpc.Status.newBuilder()
              .setCode(Code.INVALID_ARGUMENT_VALUE)
              .setMessage("grpc-status-message-value +*~")
              .build(), md);
        responseObserver.onError(t);
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testTrailersOnly(should);
  }
*/

  @Test
  public void testHandleCancel(TestContext should) {

    Async test = should.async();
    StreamingGrpc.StreamingImplBase impl = new StreamingGrpc.StreamingImplBase() {
      @Override
      public StreamObserver<Item> pipe(StreamObserver<Item> responseObserver) {
        return new StreamObserver<Item>() {
          @Override
          public void onNext(Item value) {
            responseObserver.onNext(value);
          }
          @Override
          public void onError(Throwable t) {
            should.assertEquals(t.getClass(), StatusRuntimeException.class);
            should.assertEquals(Status.Code.CANCELLED, ((StatusRuntimeException)t).getStatus().getCode());
            test.complete();
          }
          @Override
          public void onCompleted() {
          }
        };
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testHandleCancel(should);
  }

  @Test
  public void testTimeoutOnServerBeforeSendingResponse(TestContext should) throws Exception {
    Async async = should.async();
    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        Context current = Context.current();
        should.assertNotNull(current.getDeadline());
        async.complete();
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testTimeoutOnServerBeforeSendingResponse(should);
  }

  @Test
  public void testCallAttributes(TestContext should) {

    AtomicInteger testAttributesStep = new AtomicInteger();

    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    ServerInterceptor interceptor = new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        Attributes attributes = call.getAttributes();
        should.assertNotNull(attributes);
        should.assertNotNull(attributes.get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR));
        should.assertNotNull(attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
        testAttributesStep.incrementAndGet();
        return next.startCall(call, headers);
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(ServerInterceptors.intercept(impl, interceptor));
    serverStub.bind(server);
    startServer(server);
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
    HelloRequest request = HelloRequest.newBuilder().setName("Julien").build();
    HelloReply res = stub.sayHello(request);
    should.assertEquals(1, testAttributesStep.get());
  }

  @Test
  public void testCallNetworkInterrupted(TestContext should) throws InterruptedException, IOException {
    AtomicInteger requestCount = new AtomicInteger();
    Promise<Void> completed = Promise.promise();
    Async async = should.async();

    StreamingGrpc.StreamingImplBase impl = new StreamingImplBase() {
      @Override
      public StreamObserver<Item> pipe(StreamObserver<Item> responseObserver) {
        return new StreamObserver<>() {
          @Override
          public void onNext(Item item) {
            requestCount.incrementAndGet();
          }

          @Override
          public void onError(Throwable throwable) {
            completed.fail(throwable);
            async.complete();
          }

          @Override
          public void onCompleted() {
            completed.complete();
            async.complete();
          }
        };
      }
    };

    GrpcServer server = GrpcServer.server(vertx);
    GrpcServiceBridge serverStub = GrpcServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    try (var proxyServer = new ProxyServer(vertx, port + 1, port)) {
      proxyServer.start();

      int proxyPort = proxyServer.proxyServer.actualPort();
      Channel channel = ManagedChannelBuilder.forAddress("localhost", proxyPort).usePlaintext().build();
      StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);
      StreamObserver<Item> requestObserver = stub.pipe(new NoopStreamObserver<>());
      Item request = Item.newBuilder().setValue("item").build();
      requestObserver.onNext(request);
      requestObserver.onNext(request);
      requestObserver.onNext(request);

      // waiting for the connection to be established.
      Thread.sleep(1000);
    }

    async.await(20_000);

    should.assertEquals(requestCount.get(), 3);
    should.assertTrue(completed.future().failed());
  }

  static class NoopStreamObserver<T> implements StreamObserver<T> {
    @Override public void onNext(T ignored) {}

    @Override public void onError(Throwable ignored) {}

    @Override public void onCompleted() {}
  }

  static class ProxyServer implements AutoCloseable {

    private final int listenPort;

    private final int targetPort;

    private final NetServer proxyServer;

    private final NetClient proxyClient;

    // live or dead
    private final List<Map.Entry<NetSocket, NetSocket>> sockets = new ArrayList<>();

    ProxyServer(Vertx vertx, int listenPort, int targetPort) {
      this.listenPort = listenPort;
      this.targetPort = targetPort;
      this.proxyServer = vertx.createNetServer().connectHandler(this::handle);
      this.proxyClient = vertx.createNetClient();
    }

    void start() {
      this.proxyServer.listen(listenPort).toCompletionStage().toCompletableFuture().join();
    }

    void handle(NetSocket socket) {
      socket.pause();

      proxyClient.connect(targetPort, "localhost")
        .onComplete(ar -> {
          if (ar.succeeded()) {
            NetSocket proxySocket = ar.result();
            proxySocket.pause();

            socket.handler(proxySocket::write);
            proxySocket.handler(socket::write);
            socket.closeHandler(ignored -> proxySocket.close());
            proxySocket.closeHandler(ignored -> socket.close());

            sockets.add(Map.entry(socket, proxySocket));

            proxySocket.resume();
            socket.resume();
          } else {
            socket.close();
          }
        });
    }

    @Override
    public void close() {
      this.sockets.forEach(entry -> {
        entry.getKey().close();
        entry.getValue().close();
      });
      this.proxyClient.close();
      this.proxyServer.close();
    }
  }
}
