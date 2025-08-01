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
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.*;
import io.vertx.tests.common.grpc.Empty;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ClientRequestTest extends ClientTest {

  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) throws IOException {

    super.testUnary(should, requestEncoding, responseEncoding);

    Async test = should.async(2);
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.encoding(requestEncoding);
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          should.assertEquals(responseEncoding, callResponse.encoding());
          AtomicInteger count = new AtomicInteger();
          callResponse.handler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(1, count.get());
            test.countDown();
          });
          callResponse.last()
            .onComplete(should.asyncAssertSuccess(reply -> {
              should.assertEquals("Hello Julien", reply.getMessage());
              test.countDown();
          }));
        }));
        callRequest.end(Request.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testSSL(TestContext should) throws IOException {

    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    SelfSignedCertificate cert = SelfSignedCertificate.create();
    ServerCredentials creds = TlsServerCredentials
      .newBuilder()
      .keyManager(new File(cert.certificatePath()), new File(cert.privateKeyPath()))
      .build();
    startServer(called, Grpc.newServerBuilderForPort(8443, creds));

    Async test = should.async();
    client = GrpcClient.client(vertx, new HttpClientOptions().setSsl(true)
      .setUseAlpn(true)
      .setTrustOptions(cert.trustOptions()));
    client.request(SocketAddress.inetSocketAddress(8443, "localhost"), UNARY)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.handler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(1, count.get());
            test.complete();
          });
        }));
        callRequest.end(Request.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testStatus(TestContext should) throws IOException {

    super.testStatus(should);

    Async latch1 = should.async();
    Async latch2 = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          callResponse.messageHandler(reply -> {
            should.fail();
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.UNAVAILABLE, callResponse.status());
            should.assertEquals("~Greeter temporarily unavailable...~", callResponse.statusMessage());
            latch1.complete();
          });
          callResponse.last().onComplete(should.asyncAssertFailure(err -> {
            should.assertTrue(err instanceof InvalidStatusException);
            should.assertEquals(GrpcStatus.OK, ((InvalidStatusException)err).expectedStatus());
            should.assertEquals(GrpcStatus.UNAVAILABLE, ((InvalidStatusException)err).actualStatus());
            latch2.complete();
          }));
        }));
        callRequest.end(Request.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testServerStreaming(TestContext should) throws IOException {

    super.testServerStreaming(should);

    final Async test = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SOURCE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.handler(item -> {
            int i = count.getAndIncrement();
            should.assertEquals("the-value-" + i, item.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(NUM_ITEMS, count.get());
            test.complete();
          });
        }));
        callRequest.end(Empty.getDefaultInstance());
      }));
  }


  @Test
  public void testServerStreamingBackPressure(TestContext should) throws IOException {
    super.testServerStreamingBackPressure(should);

    Async test = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SOURCE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          callResponse.pause();
          AtomicInteger num = new AtomicInteger();
          Runnable readBatch = () -> {
            vertx.<Integer>executeBlocking(() -> {
              while (batchQueue.size() == 0) {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException e) {
                }
              }
              return batchQueue.poll();
            }).onSuccess(toRead -> {
              num.set(toRead);
              callResponse.resume();
            });
          };
          readBatch.run();
          callResponse.messageHandler(item -> {
            if (num.decrementAndGet() == 0) {
              callResponse.pause();
              readBatch.run();
            }
          });
          callResponse.endHandler(v -> {
            should.assertEquals(-1, num.get());
            test.complete();
          });
        }));
        callRequest.end(Empty.getDefaultInstance());
      }));
  }

  @Override
  public void testClientStreaming(TestContext should) throws Exception {

    super.testClientStreaming(should);

    Async done = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SINK)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.messageHandler(item -> {
            count.incrementAndGet();
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(1, count.get());
            done.complete();
          });
        }));
        AtomicInteger count = new AtomicInteger(NUM_ITEMS);
        vertx.setPeriodic(10, id -> {
          int val = count.decrementAndGet();
          if (val >= 0) {
            callRequest.write(Request.newBuilder().setName("the-value-" + (NUM_ITEMS - val - 1)).build());
          } else {
            vertx.cancelTimer(id);
            callRequest.end();
          }
        });
      }));
  }

  @Test
  public void testClientStreamingBackPressure(TestContext should) throws Exception {

    super.testClientStreamingBackPressure(should);

    Async done = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SINK)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          callResponse.endHandler(v -> {
            done.complete();
          });
        }));
        AtomicInteger batchCount = new AtomicInteger(0);
        Runnable[] write = new Runnable[1];
        AtomicInteger written = new AtomicInteger();
        write[0] = () -> {
          written.incrementAndGet();
          callRequest.write(Request.newBuilder().setName("the-value-" + batchCount).build());
          if (callRequest.writeQueueFull()) {
            batchQueue.add(written.getAndSet(0));
            callRequest.drainHandler(v -> {
              if (batchCount.incrementAndGet() < NUM_BATCHES) {
                write[0].run();
              } else {
                callRequest.end();
              }
            });
          } else {
            vertx.runOnContext(v -> {
              write[0].run();
            });
          }
        };
        write[0].run();
      }));
  }

  @Override
  public void testClientStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {

    super.testClientStreamingCompletedBeforeHalfClose(should);

    Async done = should.async(2);
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SINK)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.exceptionHandler(err -> {
          should.assertTrue(callRequest.isCancelled());
          done.countDown();
        });
        callRequest.response().onComplete(should.asyncAssertFailure(failure -> {
          should.assertEquals(GrpcErrorException.class, failure.getClass());
          GrpcErrorException f = (GrpcErrorException) failure;
          should.assertEquals(GrpcStatus.CANCELLED, f.status());
          done.countDown();
        }));
        callRequest.write(Request.newBuilder().setName("the-value").build());
      }));
  }

  @Test
  public void testBidiStreaming(TestContext should) throws Exception {

    super.testBidiStreaming(should);

    Async done = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), PIPE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.handler(item -> {
            int i = count.getAndIncrement();
            should.assertEquals("the-value-" + i, item.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(NUM_ITEMS, count.get());
            done.complete();
          });
        }));
        AtomicInteger count = new AtomicInteger(NUM_ITEMS);
        vertx.setPeriodic(10, id -> {
          int val = count.decrementAndGet();
          if (val >= 0) {
            callRequest.write(Request.newBuilder().setName("the-value-" + (NUM_ITEMS - val - 1)).build());
          } else {
            vertx.cancelTimer(id);
            callRequest.end();
          }
        });
      }));
  }

  @Test
  public void testBidiStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {

    super.testBidiStreamingCompletedBeforeHalfClose(should);

    Async done = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), PIPE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.write(Request.newBuilder().setName("the-value").build());
        callRequest.response().onComplete(should.asyncAssertSuccess(resp -> {
          resp.endHandler(v -> {
            done.complete();
          });
        }));
      }));
  }

  @Test
  public void testFail(TestContext should) throws Exception {

    super.testFail(should);

    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), PIPE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.write(Request.newBuilder().setName("item").build());
        callRequest.response().onComplete(should.asyncAssertSuccess(resp -> {
          AtomicInteger count = new AtomicInteger();
          resp.handler(item -> {
            if (count.getAndIncrement() == 0) {
              callRequest.cancel();
            }
          });
        }));
      }));
  }

  @Test
  public void testMetadata(TestContext should) throws Exception {

    super.testMetadata(should);

    Async test = should.async();
    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.headers().set("custom_request_header", "custom_request_header_value");
        callRequest.headers().set("custom_request_header-bin", Base64.getEncoder().encodeToString(new byte[] { 0,1,2 }));
        callRequest.headers().set("grpc-custom_request_header", "grpc-custom_request_header_value");
        callRequest.headers().set("grpc-custom_request_header-bin", Base64.getEncoder().encodeToString(new byte[] { 2,1,0 }));
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          should.assertEquals("custom_response_header_value", callResponse.headers().get("custom_response_header"));
          int step = testMetadataStep.getAndIncrement();
          should.assertTrue(2 <= step && step <= 3);
          AtomicInteger count = new AtomicInteger();
          callResponse.handler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals("custom_response_trailer_value", callResponse.trailers().get("custom_response_trailer"));
            should.assertEquals(1, count.get());
            should.assertEquals(4, testMetadataStep.getAndIncrement());
            test.complete();
          });
        }));
        callRequest.end(Request.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testSendResetWhenCompletedBeforeHalfClosed(TestContext should) throws Exception {
    Async test = should.async();
    vertx.createHttpServer().requestHandler(req -> {
      req.response()
        .putHeader(GrpcHeaderNames.GRPC_STATUS, "" + GrpcStatus.OK.code)
        .end();
      req.exceptionHandler(err -> {
        if (err instanceof StreamResetException) {
          test.complete();
        }
      });
    }).listen(8080, "localhost")
      .toCompletionStage()
      .toCompletableFuture()
      .get(20, TimeUnit.SECONDS);

    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.write(Request.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testCancel(TestContext should) throws Exception {

    CompletableFuture<Void> cf = new CompletableFuture<>();

    Async done = should.async();
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request item) {
            cf.complete(null);
          }
          @Override
          public void onError(Throwable t) {
            should.assertEquals(StatusRuntimeException.class, t.getClass());
            StatusRuntimeException sre = (StatusRuntimeException) t;
            should.assertEquals(Status.CANCELLED.getCode(), sre.getStatus().getCode());
            done.complete();
          }
          @Override
          public void onCompleted() {
          }
        };
      }
    });

    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SINK)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.write(Request.getDefaultInstance());
        io.vertx.core.Context ctx = vertx.getOrCreateContext();
        cf.whenComplete((v1, t) -> {
          ctx.runOnContext(v2 -> {
            should.assertFalse(callRequest.isCancelled());
            callRequest.cancel();
            should.assertTrue(callRequest.isCancelled());
            try {
              callRequest.write(Request.getDefaultInstance());
            } catch (IllegalStateException ignore) {
            }
          });
        });
      }));
  }

  @Test
  public void testIdleTimeout(TestContext should) throws Exception {

    CompletableFuture<Void> cf = new CompletableFuture<>();

    Async done = should.async(2);
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> sink(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<Request>() {
          @Override
          public void onNext(Request item) {
            cf.complete(null);
          }
          @Override
          public void onError(Throwable t) {
            should.assertEquals(StatusRuntimeException.class, t.getClass());
            StatusRuntimeException sre = (StatusRuntimeException) t;
            should.assertEquals(Status.CANCELLED.getCode(), sre.getStatus().getCode());
            done.countDown();
          }
          @Override
          public void onCompleted() {
          }
        };
      }
    });

    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SINK)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.write(Request.getDefaultInstance());
        cf.whenComplete((v, t) -> {
          long now = System.currentTimeMillis();
          callRequest.idleTimeout(1000);
          callRequest.exceptionHandler(err -> {
            should.assertTrue(System.currentTimeMillis() - now >= 1000);
            done.countDown();
          });
        });
      }));
  }

/*
  @Test
  public void testCall(TestContext should) throws IOException {

    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> plainResponseObserver) {
        ServerCallStreamObserver<HelloReply> responseObserver =
          (ServerCallStreamObserver<HelloReply>) plainResponseObserver;
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };
    startServer(called, ServerBuilder.forPort(port));

    client = GrpcClient.client(vertx);
    client.call(SocketAddress.inetSocketAddress(port, "localhost"), GreeterGrpc.getSayHelloMethod(),
        callRequest -> callRequest.end(HelloRequest.newBuilder().setName("Julien").build()),
        GrpcReadStream::last)
      .onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals("Hello Julien", reply.getMessage());
      }));
  }
*/

  @Test
  public void testTimeoutOnClient(TestContext should) throws Exception {
    super.testTimeoutOnClient(should);
    client = GrpcClient.client(vertx, new GrpcClientOptions().setScheduleDeadlineAutomatically(true));
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SINK)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest
          .timeout(1, TimeUnit.SECONDS);
        callRequest.write(Request.getDefaultInstance());
        callRequest.response().onComplete(should.asyncAssertFailure(err -> {
          should.assertTrue(err instanceof GrpcErrorException);
          GrpcErrorException failure = (GrpcErrorException) err;
          should.assertEquals(GrpcStatus.CANCELLED, failure.status());
        }));
      }));
  }

  @Test
  public void testTimeoutOnClientPropagation(TestContext should) throws Exception {
    super.testTimeoutOnClient(should);
    client = GrpcClient.client(vertx, new GrpcClientOptions().setScheduleDeadlineAutomatically(true));
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    context.runOnContext(v -> {
      context.putLocal(GrpcLocal.CONTEXT_LOCAL_KEY, AccessMode.CONCURRENT, new GrpcLocal(System.currentTimeMillis() + 1000));
      client.request(SocketAddress.inetSocketAddress(port, "localhost"), SINK)
        .onComplete(should.asyncAssertSuccess(callRequest -> {
          callRequest.write(Request.getDefaultInstance());
          callRequest.response().onComplete(should.asyncAssertFailure(err -> {
            should.assertTrue(err instanceof GrpcErrorException);
            GrpcErrorException failure = (GrpcErrorException) err;
            should.assertEquals(GrpcStatus.CANCELLED, failure.status());
          }));
        }));
    });
  }

  @Test
  public void testTimeoutPropagationToServer(TestContext should) throws Exception {
    CompletableFuture<Long> cf = new CompletableFuture<>();
    super.testTimeoutPropagationToServer(cf);
    Async done = should.async();
    client = GrpcClient.client(vertx, new GrpcClientOptions().setScheduleDeadlineAutomatically(true));
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest
          .timeout(10, TimeUnit.SECONDS);
        callRequest.end(Request.newBuilder().setName("Julien").build());
        callRequest.response().onComplete(should.asyncAssertSuccess(e -> {
          long timeRemaining = cf.getNow(-1L);
          should.assertTrue(timeRemaining > 7500);
          done.complete();
        }));
      }));
  }

  @Test
  public void testServerCancellation(TestContext should) throws Exception {

    Async done = should.async();
    startServer(new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public StreamObserver<Request> pipe(StreamObserver<Reply> responseObserver) {
        return new StreamObserver<>() {
          @Override
          public void onNext(Request item) {
          }
          @Override
          public void onError(Throwable t) {
          }
          @Override
          public void onCompleted() {
          }
        };
      }
    });

    client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), PIPE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {

        // Force the server to send cancel with a reset frame
        callRequest.timeout(10, TimeUnit.MILLISECONDS);
        callRequest.write(Request.newBuilder().setName("item").build());
        callRequest.response().onComplete(should.asyncAssertFailure(err -> {
          should.assertEquals(GrpcErrorException.class, err.getClass());
          GrpcErrorException gee = (GrpcErrorException) err;
          should.assertEquals(GrpcError.CANCELLED, gee.error());
          done.complete();
        }));
      }));
  }

  @Test
  public void testMissingResponseStatusIsUnknown(TestContext should) throws Exception {

    Async done = should.async();
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc")
        .end();
    });
    server.listen(port, "localhost")
      .toCompletionStage()
      .toCompletableFuture()
      .get(20, TimeUnit.SECONDS);

    client = GrpcClient.client(vertx);
    client
      .request(SocketAddress.inetSocketAddress(port, "localhost"), PIPE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.end();
        callRequest
          .response()
          .onComplete(should.asyncAssertSuccess(resp -> {
            resp.endHandler(v -> {
              should.assertEquals(GrpcStatus.UNKNOWN, resp.status());
              done.complete();
            });
          }));
      }));

    done.awaitSuccess();
  }

  @Test
  public void testJsonMessageFormat(TestContext should) throws Exception {

    super.testJsonMessageFormat(should, "application/grpc+json");

    JsonObject helloReply = new JsonObject().put("message", "Hello Julien");
    JsonObject helloRequest = new JsonObject().put("name", "Julien");

    Async done = should.async();

    ServiceMethod<JsonObject, JsonObject> serviceMethod = ServiceMethod.client(
      ServiceName.create("helloworld", "Greeter"),
      "SayHello",
      GrpcMessageEncoder.JSON_OBJECT,
      GrpcMessageDecoder.JSON_OBJECT);

    client = GrpcClient.client(vertx);
    client
      .request(SocketAddress.inetSocketAddress(port, "localhost"), serviceMethod)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.end(helloRequest);
        callRequest
          .response()
          .onComplete(should.asyncAssertSuccess(resp -> {
            should.assertEquals(WireFormat.JSON, resp.format());
            resp.handler(msg -> {
              should.assertEquals(helloReply, msg);
            });
            resp.endHandler(v -> {
              done.complete();
            });
          }));
      }));

    done.awaitSuccess();
  }

  @Test
  public void testDefaultMessageSizeOverflow(TestContext should) throws Exception {

    Async test = should.async();

    Request item = Request.newBuilder().setName("Asmoranomardicadaistinaculdacar").build();
    int itemLen = item.getSerializedSize();

    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        ServerCallStreamObserver callStreamObserver = (ServerCallStreamObserver) responseObserver;
        callStreamObserver.setOnCancelHandler(() -> {
          test.complete();
        });
        responseObserver.onNext(Reply.newBuilder().setMessage(item.getName()).build());
      }
    };
    startServer(called);

    GrpcClient client = GrpcClient.client(vertx, new GrpcClientOptions().setMaxMessageSize(itemLen - 1));
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SOURCE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          callResponse.handler(msg -> {
            should.fail();
          });
        }));
        callRequest.end(Empty.getDefaultInstance());
      }));

    test.awaitSuccess(20_000);
  }

  @Test
  public void testInvalidMessage(TestContext should) throws Exception {
    Async test = should.async();
    testInvalidMessage(should, callResponse -> {
      callResponse.exceptionHandler(err -> {
        should.assertEquals(MessageSizeOverflowException.class, err.getClass());
        test.complete();
      });
      callResponse.endHandler(v -> should.fail());
    });
    test.awaitSuccess(20_000);
  }

  @Test
  public void testInvalidMessageHandler(TestContext should) throws Exception {
    Async test = should.async();
    testInvalidMessage(should, callResponse -> {
      List<Object> received = new ArrayList<>();
      callResponse.invalidMessageHandler(received::add);
      callResponse.handler(msg -> should.fail());
      callResponse.endHandler(v -> {
        should.assertEquals(MessageSizeOverflowException.class, received.get(0).getClass());
        test.complete();
      });
    });
    test.awaitSuccess(20_000);
  }

  private void testInvalidMessage(TestContext should, Handler<GrpcClientResponse<Request, Reply>> responseHandler) throws Exception {
    Reply reply = Reply.newBuilder().setMessage("Asmoranomardicadaistinaculdacar").build();

    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> responseObserver) {
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
      }
    };
    startServer(called);

    GrpcClient client = GrpcClient.client(vertx, new GrpcClientOptions().setMaxMessageSize(reply.getSerializedSize() - 1));
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(responseHandler));
        callRequest.end(Request.getDefaultInstance());
      }));
  }

  @Test
  public void testInvalidMessageStream(TestContext should) throws Exception {
    Async test = should.async();
    testInvalidMessageStream(should, callResponse -> {
      List<Object> received = new ArrayList<>();
      callResponse.handler(received::add);
      callResponse.exceptionHandler(err -> {
        should.assertEquals(Reply.class, received.get(0).getClass());
        should.assertEquals(MessageSizeOverflowException.class, err.getClass());
        should.assertEquals(1, received.size());
        test.complete();
      });
      callResponse.endHandler(v -> {
        should.fail();
      });
    });
    test.awaitSuccess(20_000);
  }

  @Test
  public void testInvalidMessageHandlerStream(TestContext should) throws Exception {
    Async test = should.async();
    testInvalidMessageStream(should, callResponse -> {
      List<Object> received = new ArrayList<>();
      callResponse.invalidMessageHandler(received::add);
      callResponse.handler(received::add);
      callResponse.endHandler(v -> {
        should.assertEquals(Reply.class, received.get(0).getClass());
        should.assertEquals(MessageSizeOverflowException.class, received.get(1).getClass());
        should.assertEquals(Reply.class, received.get(2).getClass());
        should.assertEquals(3, received.size());
        test.complete();
      });
    });
    test.awaitSuccess(20_000);
  }

  public void testInvalidMessageStream(TestContext should, Handler<GrpcClientResponse<Empty, Reply>> responseHandler) throws Exception {
    List<Request> items = Arrays.asList(
      Request.newBuilder().setName("msg1").build(),
      Request.newBuilder().setName("Asmoranomardicadaistinaculdacar").build(),
      Request.newBuilder().setName("msg3").build()
    );

    int itemLen = items.get(1).getSerializedSize();

    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void source(Empty request, StreamObserver<Reply> responseObserver) {
        items.forEach(item -> responseObserver.onNext(Reply.newBuilder().setMessage(item.getName()).build()));
        responseObserver.onCompleted();
      }
    };
    startServer(called);

    GrpcClient client = GrpcClient.client(vertx, new GrpcClientOptions().setMaxMessageSize(itemLen - 1));
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), SOURCE)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(responseHandler));
        callRequest.end(Empty.getDefaultInstance());
      }));
  }
}
