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
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.tests.common.grpc.Empty;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ClientTest extends ClientTestBase {

  static final int NUM_ITEMS = 128;
  static final int NUM_BATCHES = 5;

  protected GrpcClient client;

  @Test
  public void testUnary(TestContext should) throws IOException {
    testUnary(should, "identity", "identity");
  }

  @Test
  public void testUnaryDecompression(TestContext should) throws IOException {
    testUnary(should, "identity", "gzip");
  }

  @Test
  public void testUnaryCompression(TestContext should) throws IOException {
    testUnary(should, "gzip", "identity");
  }

  @Test
  public void testUnaryCompressionDecompression(TestContext should) throws IOException {
    testUnary(should, "gzip", "gzip");
  }

  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) throws IOException {
    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> plainResponseObserver) {
        ServerCallStreamObserver<Reply> responseObserver =
          (ServerCallStreamObserver<Reply>) plainResponseObserver;
        responseObserver.setCompression(responseEncoding);
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };
    startServer(called, ServerBuilder.forPort(port).intercept(new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String encodingHeader = headers.get(Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER));
        should.assertEquals(requestEncoding, encodingHeader);
        return next.startCall(call, headers);
      }
    }));
  }

  @Test
  public void testServerStreaming(TestContext should) throws IOException {
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        for (int i = 0;i < NUM_ITEMS;i++) {
          responseObserver.onNext(Reply.newBuilder().setMessage("the-value-" + i).build());
        }
        responseObserver.onCompleted();
      }
    });
  }

  protected final ConcurrentLinkedDeque<Integer> batchQueue = new ConcurrentLinkedDeque<>();

  @Test
  public void testServerStreamingBackPressure(TestContext should) throws IOException {
    batchQueue.clear();
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        ServerCallStreamObserver obs = (ServerCallStreamObserver) responseObserver;
        AtomicInteger numRounds = new AtomicInteger(20);
        Runnable readyHandler = () -> {
          if (numRounds.decrementAndGet() > 0) {
            int num = 0;
            while (obs.isReady()) {
              num++;
              Reply item = Reply.newBuilder().setMessage("the-value-" + num).build();
              responseObserver.onNext(item);
            }
            batchQueue.add(num);
          } else {
            batchQueue.add(-1);
            responseObserver.onCompleted();
          }
        };
        obs.setOnReadyHandler(readyHandler);
      }
    });
  }

  @Test
  public void testClientStreaming(TestContext should) throws Exception {
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<Request>() {
          final List<String> items = new ArrayList<>();
          @Override
          public void onNext(Request item) {
            items.add(item.getName());
          }
          @Override
          public void onError(Throwable t) {
            should.fail(t);
          }
          @Override
          public void onCompleted() {
            List<String> expected = IntStream.rangeClosed(0, NUM_ITEMS - 1).mapToObj(val -> "the-value-" + val).collect(Collectors.toList());
            should.assertEquals(expected, items);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
          }
        };
      }
    });
  }

  @Test
  public void testClientStreamingBackPressure(TestContext should) throws Exception {
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        return sink((ServerCallStreamObserver<Empty>) responseObserver);
      }
      private AtomicBoolean completed = new AtomicBoolean();
      private AtomicInteger toRead = new AtomicInteger();
      final AtomicInteger batchCount = new AtomicInteger();
      private void waitForBatch(ServerCallStreamObserver<Empty> responseObserver) {
        if (batchCount.get() < NUM_BATCHES) {
          batchCount.incrementAndGet();
          new Thread(() -> {
            while (batchQueue.isEmpty()) {
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
            Integer num = batchQueue.poll();
            toRead.addAndGet(num);
            responseObserver.request(num);
          }).start();
        } else if (completed.get()) {
          responseObserver.onNext(Empty.getDefaultInstance());
          responseObserver.onCompleted();
        }
      }
      private StreamObserver<Request> sink(ServerCallStreamObserver<Empty> responseObserver) {
        responseObserver.disableAutoRequest();
        waitForBatch(responseObserver);
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request item) {
            should.assertEquals("the-value-" + (batchCount.get() - 1), item.getName());
            if (toRead.decrementAndGet() == 0) {
              waitForBatch(responseObserver);
            }
          }
          @Override
          public void onError(Throwable t) {
            should.fail(t);
          }
          @Override
          public void onCompleted() {
            completed.set(true);
            if (batchCount.get() == NUM_BATCHES) {
              responseObserver.onNext(Empty.getDefaultInstance());
              responseObserver.onCompleted();
            }
          }
        };
      }
    });
  }

  @Test
  public void testClientStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {
    Async latch = should.async();
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<>() {
          @Override
          public void onNext(Request item) {
            responseObserver.onCompleted();
          }
          @Override
          public void onError(Throwable t) {
            latch.complete();
          }
          @Override
          public void onCompleted() {
            should.fail();
          }
        };
      }
    });
  }

  @Test
  public void testBidiStreaming(TestContext should) throws Exception {
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> pipe(StreamObserver<Reply> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request value) {
            responseObserver.onNext(Reply.newBuilder().setMessage(value.getName()).build());
          }
          @Override
          public void onError(Throwable t) {
            responseObserver.onError(t);
          }
          @Override
          public void onCompleted() {
            responseObserver.onCompleted();
          }
        };
      }
    });
  }

  @Test
  public void testBidiStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> pipe(StreamObserver<Reply> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request value) {
            responseObserver.onCompleted();
          }
          @Override
          public void onError(Throwable t) {
            should.fail(t);
          }
          @Override
          public void onCompleted() {
            should.fail();
          }
        };
      }
    });
  }

  @Test
  public void testStatus(TestContext should) throws IOException {

    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onError(Status.UNAVAILABLE
          .withDescription("~Greeter temporarily unavailable...~").asRuntimeException());
      }
    };
    startServer(called);
  }

  @Test
  public void testFail(TestContext should) throws Exception {

    Async done = should.async();
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> pipe(StreamObserver<Reply> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request item) {
            responseObserver.onNext(Reply.newBuilder().setMessage(item.getName()).build());
          }
          @Override
          public void onError(Throwable t) {
            should.assertEquals(StatusRuntimeException.class, t.getClass());
            StatusRuntimeException ex = (StatusRuntimeException) t;
            should.assertEquals(Status.CANCELLED.getCode(), ex.getStatus().getCode());
            done.complete();
          }
          @Override
          public void onCompleted() {
          }
        };
      }
    });
  }

  protected AtomicInteger testMetadataStep;

  @Test
  public void testMetadata(TestContext should) throws Exception {

    testMetadataStep = new AtomicInteger();
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
            headers.put(Metadata.Key.of("custom_response_header-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[] { 0,1,2 });
            headers.put(Metadata.Key.of("grpc-custom_response_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "grpc-custom_response_header_value");
            headers.put(Metadata.Key.of("grpc-custom_response_header-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[] { 2,1,0 });
            should.assertEquals(1, testMetadataStep.getAndIncrement());
            super.sendHeaders(headers);
          }
          @Override
          public void close(Status status, Metadata trailers) {
            trailers.put(Metadata.Key.of("custom_response_trailer", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "custom_response_trailer_value");
            trailers.put(Metadata.Key.of("custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[] { 0,1,2 });
            trailers.put(Metadata.Key.of("grpc-custom_response_trailer", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "grpc-custom_response_trailer_value");
            trailers.put(Metadata.Key.of("grpc-custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[] { 2,1,0 });
            int step = testMetadataStep.getAndIncrement();
            should.assertTrue(2 <= step && step <= 3);
            super.close(status, trailers);
          }
        },headers);
      }
    };

    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> plainResponseObserver) {
        ServerCallStreamObserver<Reply> responseObserver =
          (ServerCallStreamObserver<Reply>) plainResponseObserver;
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };
    startServer(ServerInterceptors.intercept(called, interceptor));

  }

  protected static void assertEquals(TestContext should, byte[] expected, byte[] actual) {
    should.assertNotNull(actual);
    should.assertTrue(Arrays.equals(expected, actual));
  }

  protected static void assertEquals(TestContext should, byte[] expected, String actual) {
    should.assertNotNull(actual);
    should.assertTrue(Arrays.equals(expected, Base64.getDecoder().decode(actual)));
  }

  public void testTimeoutOnClient(TestContext should) throws Exception {
    Async done = should.async();
    Async listenLatch = should.async();
    HttpServer server = vertx.createHttpServer();
    server
      .requestHandler(request -> {
        String timeout = request.getHeader(GrpcHeaderNames.GRPC_TIMEOUT);
        should.assertNotNull(timeout);
        request.response().exceptionHandler(err -> {
          should.assertEquals(StreamResetException.class, err.getClass());
          StreamResetException reset = (StreamResetException) err;
          should.assertEquals(8L, reset.getCode());
          done.complete();
        });
      })
      .listen(port, "localhost")
      .onComplete(should.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess(20_000);
  }

  public void testTimeoutPropagationToServer(CompletableFuture<Long> cf) throws Exception {
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        long timeRemaining = Context.current().getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
        cf.complete(timeRemaining);
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    });
  }

  @Test
  public abstract void testJsonMessageFormat(TestContext should) throws Exception;

  public void testJsonMessageFormat(TestContext should, String expectedContentType) throws Exception {

    JsonObject helloReply = new JsonObject().put("message", "Hello Julien");
    JsonObject helloRequest = new JsonObject().put("name", "Julien");

    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      should.assertEquals(expectedContentType, req.getHeader(HttpHeaders.CONTENT_TYPE));
      req.body().onComplete(should.asyncAssertSuccess(body -> {
        int len = body.getInt(1);
        JsonObject actual = (JsonObject) Json.decodeValue(body.getBuffer(5, 5 + len));
        should.assertEquals(helloRequest, actual);
        Buffer json = helloReply.toBuffer();
        req.response()
          .putHeader(HttpHeaders.CONTENT_TYPE, expectedContentType)
          .putTrailer(GrpcHeaderNames.GRPC_STATUS, "0")
          .end(Buffer
            .buffer()
            .appendByte((byte)0)
            .appendInt(json.length())
            .appendBuffer(json)
          );
      }));
    });
    server.listen(port, "localhost")
      .toCompletionStage()
      .toCompletableFuture()
      .get(20, TimeUnit.SECONDS);
  }
}
