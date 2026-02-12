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
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpcio.common.impl.Utils;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import io.vertx.tests.common.grpc.*;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerBridgeTest extends ServerTest {

  @Override
  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding, DecompressorRegistry decompressors) {
    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        if (!responseEncoding.equals("identity")) {
          ((ServerCallStreamObserver<?>)responseObserver).setCompression(responseEncoding);
        }
        if (!requestEncoding.equals("identity")) {
          // No way to check the request encoding with the API
        }
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testUnary(should, requestEncoding, responseEncoding, decompressors);
  }

  @Test
  public void testUnaryInterceptor(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
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

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(def);
    serverStub.bind(server);
    startServer(server);

    super.testUnary(should, "identity", "identity", DecompressorRegistry.getDefaultInstance());
  }

  @Test
  public void testStatusUnary1(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onError(new StatusRuntimeException(Status.ALREADY_EXISTS.withDescription("status-msg")));
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testStatusUnary(should, Status.ALREADY_EXISTS, "status-msg");
  }

  @Test
  public void testStatusUnary2(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onError(new RuntimeException("should-be-ignored"));
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testStatusUnary(should, Status.UNKNOWN, null);
  }

  @Test
  public void testStatusStreaming(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(Reply.newBuilder().setMessage("msg1").build());
        responseObserver.onNext(Reply.newBuilder().setMessage("msg2").build());
        responseObserver.onError(new StatusRuntimeException(Status.ALREADY_EXISTS));
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testStatusStreaming(should, Status.ALREADY_EXISTS, "msg1", "msg2");
  }

  @Override
  public void testServerStreaming(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        for (int i = 0; i < NUM_ITEMS; i++) {
          Reply item = Reply.newBuilder().setMessage("the-value-" + i).build();
          responseObserver.onNext(item);
        }
        responseObserver.onCompleted();
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testServerStreaming(should);
  }

  @Override
  public void testClientStreaming(TestContext should) throws Exception {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<Request>() {
          int seq = 0;
          @Override
          public void onNext(Request value) {
            should.assertEquals(value.getName(), "the-value-" + seq++);
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

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testClientStreaming(should);
  }

  @Override
  public void testClientStreamingCompletedBeforeHalfClose(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request value) {
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

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testClientStreamingCompletedBeforeHalfClose(should);

  }

  @Override
  public void testBidiStreaming(TestContext should) throws Exception {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> pipe(StreamObserver<Reply> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request value) {
            responseObserver.onNext(Reply.newBuilder().setMessage(value.getName()).build());
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

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testBidiStreaming(should);
  }

  @Override
  public void testBidiStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> pipe(StreamObserver<Reply> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request value) {
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

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testBidiStreamingCompletedBeforeHalfClose(should);
  }

  @Override
  public void testUnknownService(TestContext should) {

    GrpcIoServer server = GrpcIoServer.server(vertx);
    startServer(server);

    super.testUnknownService(should);
  }

  @Override
  public void testMetadata(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
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

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(ServerInterceptors.intercept(impl, interceptor));
    serverStub.bind(server);
    startServer(server);

    super.testMetadata(should);
  }

  @Test
  public void testEarlyHeadersOk(TestContext should) {
    testEarlyHeaders(GrpcStatus.OK, should);
  }

  @Test
  public void testEarlyHeadersInvalidArgument(TestContext should) {
    testEarlyHeaders(GrpcStatus.INVALID_ARGUMENT, should);
  }

  private void testEarlyHeaders(GrpcStatus status, TestContext should) {

    AtomicReference<Runnable> continuation = new AtomicReference<>();

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        continuation.set(() -> {
          responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
          if (status == GrpcStatus.OK) {
            responseObserver.onCompleted();
          } else {
            responseObserver.onError(new StatusRuntimeException(Status.fromCodeValue(status.code)));
          }
        });
      }
    };

    Metadata.Key<String> HEADER = Metadata.Key.of("xx-acme-header", Metadata.ASCII_STRING_MARSHALLER);

    ServerServiceDefinition def = ServerInterceptors.intercept(impl, new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> wrappedServerCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
          @Override
          public void sendHeaders(Metadata headers) {
            // Already done
          }
        };
        Metadata metadata = new Metadata();
        metadata.put(HEADER, "whatever");
        call.sendHeaders(metadata);
        return next.startCall(wrappedServerCall, headers);
      }
    });

    GrpcIoServer server = GrpcIoServer.server(vertx);
    server.addService(GrpcIoServiceBridge.bridge(def));
    startServer(server);

    super.testEarlyHeaders(should, status, () -> continuation.get().run());
  }

  @Override
  public void testTrailersOnly(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        final StatusRuntimeException t = new StatusRuntimeException(Status.INVALID_ARGUMENT);
        responseObserver.onError(t);
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    server.addService(impl);
    startServer(server);

    super.testTrailersOnly(should);
  }

  @Override
  public void testDistinctHeadersAndTrailers(TestContext should) {

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        responseObserver.onCompleted();
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    server.addService(impl);
    startServer(server);

    super.testDistinctHeadersAndTrailers(should);
  }

  @Test
  public void testHandleCancel(TestContext should) {

    Async test = should.async();
    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> pipe(StreamObserver<Reply> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request value) {
            responseObserver.onNext(Reply.newBuilder().setMessage(value.getName()).build());
          }
          @Override
          public void onError(Throwable t) {
            should.assertEquals(t.getClass(), StatusRuntimeException.class);
            should.assertEquals(Status.Code.CANCELLED, ((StatusRuntimeException)t).getStatus().getCode());
            should.assertTrue(((ServerCallStreamObserver<?>)responseObserver).isCancelled());
            test.complete();
          }
          @Override
          public void onCompleted() {
          }
        };
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testHandleCancel(should);
  }

  @Test
  public void testTimeoutOnServerBeforeSendingResponse(TestContext should) throws Exception {
    Async async = should.async();
    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        Context current = Context.current();
        should.assertNotNull(current.getDeadline());
        async.complete();
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    super.testTimeoutOnServerBeforeSendingResponse(should);
  }

  @Test
  public void testCallAttributes(TestContext should) {

    AtomicInteger testAttributesStep = new AtomicInteger();

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
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

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(ServerInterceptors.intercept(impl, interceptor));
    serverStub.bind(server);
    startServer(server);
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Request request = Request.newBuilder().setName("Julien").build();
    Reply res = stub.unary(request);
    should.assertEquals(1, testAttributesStep.get());
  }

  @Test
  public void testJsonMessageFormat(TestContext should) throws Exception {

    MethodDescriptor<Request, Reply> sayHello =
      MethodDescriptor.newBuilder(
          Utils.<Request>marshallerFor(Request::newBuilder),
          Utils.<Reply>marshallerFor(Reply::newBuilder))
        .setFullMethodName(
          MethodDescriptor.generateFullMethodName(TestConstants.TEST_SERVICE.fullyQualifiedName(), "Unary"))
        .setType(MethodDescriptor.MethodType.UNARY)
        .build();

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(() -> {
      ServiceDescriptor desc = TestServiceGrpc.getServiceDescriptor();
      return io.grpc.ServerServiceDefinition.builder(ServiceDescriptor.newBuilder(desc.getName())
          .setSchemaDescriptor(desc.getSchemaDescriptor())
          .addMethod(sayHello)
          .build())
        .addMethod(
          sayHello,
          ServerCalls.asyncUnaryCall(impl::unary))
        .build();
    });
    serverStub.bind(server);
    startServer(server);

    super.testJsonMessageFormat(should, "application/grpc");
  }

  @Test
  public void testResumeFlowControlOnServerClose(TestContext should) throws Exception {
    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        io.vertx.core.Context context = Vertx.currentContext();
        context.exceptionHandler(err -> {

        });
        return new StreamObserver<>() {
          boolean errorSent;
          @Override
          public void onNext(Request value) {
            if (errorSent) {
              should.fail();
              return;
            }
            errorSent = true;
            responseObserver.onError(Status.FAILED_PRECONDITION.asException());
          }
          @Override
          public void onError(Throwable t) {
          }
          @Override
          public void onCompleted() {
          }
        };
      }
    };

    GrpcIoServer server = GrpcIoServer.server(vertx);
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(impl);
    serverStub.bind(server);
    startServer(server);

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    for (int i = 0; i < 3; i++) {
      CountDownLatch finishLatch = new CountDownLatch(1);
      var requestObserver = stub.sink(new StreamObserver<>() {
        @Override
        public void onNext(Empty response) {
        }
        @Override
        public void onError(Throwable t) {
          finishLatch.countDown();
        }
        @Override
        public void onCompleted() {
        }
      });
      int n = 6000;
      var req = Request.newBuilder().setName("1234567890").build();
      try {
        for (int j = 0; j < n; j++) {
          requestObserver.onNext(req);
          if (finishLatch.getCount() == 0) {
            return;
          }
        }
      } catch (RuntimeException e) {
        should.fail(e);
      }
      requestObserver.onCompleted();
      if (!finishLatch.await(10, TimeUnit.SECONDS)) {
        should.fail();
      }
    }
  }
}
