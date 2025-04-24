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
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.MultiMap;
import io.vertx.core.Timer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.tests.common.grpc.Empty;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerRequestTest extends ServerTest {

  @Override
  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) {
    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        if (!requestEncoding.equals("identity")) {
          should.assertEquals(requestEncoding, call.encoding());
        }
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .encoding(responseEncoding)
          .end(helloReply);
      });
    }));

    super.testUnary(should, requestEncoding, responseEncoding);
  }

  @Test
  public void testSSL(TestContext should) throws IOException {

    SelfSignedCertificate cert = SelfSignedCertificate.create();

    startServer(new HttpServerOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setPort(8443)
      .setHost("localhost")
      .setKeyCertOptions(cert.keyCertOptions()), GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .end(helloReply);
      });
    }));

    ChannelCredentials creds = TlsChannelCredentials.newBuilder().trustManager(new File(cert.certificatePath())).build();
    channel = Grpc.newChannelBuilderForAddress("localhost", 8443, creds).build();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Request request = Request.newBuilder().setName("Julien").build();
    Reply res = stub.unary(request);
    should.assertEquals("Hello Julien", res.getMessage());
  }

  @Override
  public void testStatus(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.handler(helloRequest -> {
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .status(GrpcStatus.UNAVAILABLE)
          .end();
      });
    }));

    super.testStatus(should);
  }

  @Override
  public void testServerStreaming(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(SOURCE, call -> {
      for (int i = 0; i < NUM_ITEMS; i++) {
        Reply item = Reply.newBuilder().setMessage("the-value-" + i).build();
        call.response().write(item);
      }
      call.response().end();
    }));

    super.testServerStreaming(should);
  }

  @Override
  public void testClientStreaming(TestContext should) throws Exception {

    startServer(GrpcServer.server(vertx).callHandler(SINK, call -> {
      call.handler(item -> {
        // Should assert item
      });
      call.endHandler(v -> {
        call.response().end(Empty.getDefaultInstance());
      });
    }));

    super.testClientStreaming(should);
  }

  @Override
  public void testClientStreamingCompletedBeforeHalfClose(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(SINK, call -> {
      call.handler(item -> {
        call.response().status(GrpcStatus.CANCELLED).end();
      });
      call.endHandler(v -> {
        should.fail();
      });
    }));

    super.testClientStreamingCompletedBeforeHalfClose(should);
  }

  @Override
  public void testBidiStreaming(TestContext should) throws Exception {

    startServer(GrpcServer.server(vertx).callHandler(PIPE, call -> {
      call.handler(item -> {
        call.response().write(Reply.newBuilder().setMessage(item.getName()).build());
      });
      call.endHandler(v -> {
        call.response().end();
      });
    }));

    super.testBidiStreaming(should);
  }

  @Override
  public void testBidiStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {

    Async done = should.async();
    startServer(GrpcServer.server(vertx).callHandler(PIPE, call -> {
      call.handler(item -> {
        call.response().end();
        call.errorHandler(err -> {
          should.assertEquals(GrpcError.CANCELLED, err);
          done.complete();
        });
      });
    }));

    super.testBidiStreamingCompletedBeforeHalfClose(should);
  }

  @Test
  public void testMetadata(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      should.assertEquals(0, testMetadataStep.getAndIncrement());
      MultiMap headers = call.headers();
      should.assertEquals("custom_request_header_value", headers.get("custom_request_header"));
      assertEquals(should, new byte[]{ 0,1,2 }, headers.get("custom_request_header-bin"));
      should.assertEquals("grpc-custom_request_header_value", headers.get("grpc-custom_request_header"));
      assertEquals(should, new byte[] { 2,1,0 }, headers.get("grpc-custom_request_header-bin"));
      call.handler(helloRequest -> {
        should.assertEquals(1, testMetadataStep.getAndAdd(2));
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response.headers().set("custom_response_header", "custom_response_header_value");
        response.headers().set("custom_response_header-bin", Base64.getEncoder().encodeToString(new byte[]{0,1,2}));
        response.headers().set("grpc-custom_response_header", "grpc-custom_response_header_value");
        response.headers().set("grpc-custom_response_header-bin", Base64.getEncoder().encodeToString(new byte[]{2,1,0}));
        response.trailers().set("custom_response_trailer", "custom_response_trailer_value");
        response.trailers().set("custom_response_trailer-bin", Base64.getEncoder().encodeToString(new byte[]{0,1,2}));
        response.trailers().set("grpc-custom_response_trailer", "grpc-custom_response_trailer_value");
        response.trailers().set("grpc-custom_response_trailer-bin", Base64.getEncoder().encodeToString(new byte[]{2,1,0}));
        response
          .end(helloReply);
      });
    }));

    super.testMetadata(should);
  }

  @Test
  public void testFailInHeaders(TestContext should) {
    testFail(should, 0);
  }

  @Test
  public void testFailInTrailers(TestContext should) {
    testFail(should, 1);
  }

  private void testFail(TestContext should, int numMsg) {
    startServer(GrpcServer.server(vertx).callHandler(PIPE, call -> {
      call.handler(item -> {
        for (int i = 0;i < numMsg;i++) {
          call.response().write(Reply.newBuilder().setMessage(item.getName()).build());
        }
        call.response().status(GrpcStatus.UNAVAILABLE).end();
      });
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async done = should.async();
    ClientCallStreamObserver<Request> items = (ClientCallStreamObserver<Request>) stub.pipe(new StreamObserver<Reply>() {
      AtomicInteger count = new AtomicInteger();
      @Override
      public void onNext(Reply value) {
        count.getAndIncrement();
      }
      @Override
      public void onError(Throwable t) {
        should.assertEquals(StatusRuntimeException.class, t.getClass());
        StatusRuntimeException sre = (StatusRuntimeException) t;
        should.assertEquals(Status.UNAVAILABLE.getCode(), sre.getStatus().getCode());
        should.assertEquals(numMsg, count.get());
        done.complete();
      }
      @Override
      public void onCompleted() {
      }
    });
    items.onNext(Request.newBuilder().setName("the-value").build());
  }

  @Test
  public void testHandleCancel(TestContext should) {

    Async test = should.async();
    startServer(GrpcServer.server(vertx).callHandler(PIPE, call -> {
      call.errorHandler(error -> {
        should.assertEquals(GrpcError.CANCELLED, error);
        test.complete();
      });
      call.handler(item -> {
        call.response().write(Reply.newBuilder().setMessage(item.getName()).build());
      });
    }));

    super.testHandleCancel(should);
  }

  @Override
  public void testTrailersOnly(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.handler(helloRequest -> {
        GrpcServerResponse<Request, Reply> response = call.response();
        response.statusMessage("grpc-status-message-value +*~");
        response.trailers().set("custom_response_trailer", "custom_response_trailer_value");
        response.trailers().set("custom_response_trailer-bin", Base64.getEncoder().encodeToString(new byte[] { 0,1,2 }));
        response.trailers().set("grpc-custom_response_trailer", "grpc-custom_response_trailer_value");
        response.trailers().set("grpc-custom_response_trailer-bin", Base64.getEncoder().encodeToString(new byte[] { 2,1,0 }));
        response
          .status(GrpcStatus.INVALID_ARGUMENT)
          .end();
      });
    }));

    super.testTrailersOnly(should);
  }

  @Test
  public void testCancel(TestContext should) {

    Async test = should.async();

    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      GrpcServerResponse<Request, Reply> response = call.response();
      response.cancel();
      try {
        response.write(Reply.newBuilder().build());
      } catch (IllegalStateException e) {
        test.complete();
      }
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);

    Request request = Request.newBuilder().setName("Julien").build();
    try {
      stub.unary(request);
    } catch (StatusRuntimeException ignore) {
      should.assertEquals(Status.CANCELLED.getCode(), ignore.getStatus().getCode());
    }
  }

  @Test
  public void testTimeoutPropagation(TestContext should) {
    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      GrpcServerResponse<Request, Reply> response = call.response();
      long timeout = call.timeout();
      long limit = TimeUnit.SECONDS.toMillis(7);
      should.assertTrue(limit <= timeout);
      call.messageHandler(hello -> {
      });
      call.endHandler(v -> {
        response.end(Reply.newBuilder().build());
      });
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);

    Request request = Request.newBuilder().setName("Julien").build();
    Reply resp = stub.withDeadlineAfter(10, TimeUnit.SECONDS).unary(request);
  }

  @Test
  public void testTimeoutOnServerBeforeSendingResponse(TestContext should) throws Exception {
    Async async = should.async();
    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setScheduleDeadlineAutomatically(true)).callHandler(UNARY, call -> {
      should.assertTrue(call.timeout() > 0L);
      Timer deadline = call.deadline();
      should.assertNotNull(deadline);
      should.assertTrue(deadline.getDelay(TimeUnit.MILLISECONDS) > 0L);
      GrpcServerResponse<Request, Reply> response = call.response();
      async.complete();
    }));

    super.testTimeoutOnServerBeforeSendingResponse(should);
  }

  @Test
  public void testTimeoutOnServerAfterSendingResponse(TestContext should) throws Exception {
    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setScheduleDeadlineAutomatically(true)).callHandler(UNARY, call -> {
      GrpcServerResponse<Request, Reply> response = call.response();
      response.end();
    }));

    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));
    Async async = should.async();
    client.request(HttpMethod.POST, port, "localhost", "/io.vertx.tests.common.grpc.tests.TestService/Unary")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(GrpcHeaderNames.GRPC_TIMEOUT, TimeUnit.SECONDS.toMillis(1) + "m");
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          resp.endHandler(v -> {
            String status = resp.getTrailer("grpc-status");
            should.assertEquals(String.valueOf(GrpcStatus.OK.code), status);
            async.complete();
          });
        }));
        req.sendHead();
      }));

    async.awaitSuccess();
  }

  @Test
  public void testTimeoutPropagationOnServer(TestContext should) throws Exception {
    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setDeadlinePropagation(true)).callHandler(UNARY, call -> {
      GrpcServerResponse<Request, Reply> response = call.response();
      GrpcLocal local = ((ContextInternal)vertx.getOrCreateContext()).getLocal(GrpcLocal.CONTEXT_LOCAL_KEY);
      should.assertNotNull(local);
      should.assertTrue(local.deadline().toEpochMilli() - System.currentTimeMillis() > 8000);
      response.end();
    }));

    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));
    Async async = should.async();
    client.request(HttpMethod.POST, port, "localhost", "/helloworld.Greeter/SayHello")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(GrpcHeaderNames.GRPC_TIMEOUT, "10S");
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          resp.endHandler(v -> {
            async.complete();
          });
        }));
        req.sendHead();
      }));

    async.awaitSuccess();
  }

  @Test
  public void testJsonMessageFormat(TestContext should) throws Exception {

    JsonObject helloReply = new JsonObject().put("message", "Hello Julien");
    JsonObject helloRequest = new JsonObject().put("name", "Julien");

    startServer(GrpcServer.server(vertx).callHandler(UNARY_JSON, call -> {
      call.last().onComplete(should.asyncAssertSuccess(msg -> {
        should.assertEquals(helloRequest, msg);
        call.response().end(helloReply);
      }));
    }));

    super.testJsonMessageFormat(should, "application/grpc+json");
  }

  @Test
  public void testDefaultMessageSizeOverflow(TestContext should) {

    Request request = Request.newBuilder().setName("Asmoranomardicadaistinaculdacar").build();
    int requestLen = request.getSerializedSize();

    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(requestLen - 1))
      .callHandler(UNARY, call -> {
      }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);

    try {
      stub.unary(request);
      should.fail();
    } catch (StatusRuntimeException ignore) {
      should.assertEquals(Status.RESOURCE_EXHAUSTED.getCode(), ignore.getStatus().getCode());
    }
  }

  @Test
  public void testInvalidMessageHandler(TestContext should) {

    Request request = Request.newBuilder().setName("Asmoranomardicadaistinaculdacar").build();
    int requestLen = request.getSerializedSize();

    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(requestLen - 1))
      .callHandler(UNARY, call -> {
        AtomicInteger invalid = new AtomicInteger();
        call.handler(msg -> {
          should.fail();
        });
        call.invalidMessageHandler(err -> {
          should.assertEquals(0, invalid.getAndIncrement());
        });
        call.endHandler(v -> {
          call.response().end(Reply.newBuilder().setMessage("Hola").build());
        });
      }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);

    Reply resp = stub.unary(request);
    should.assertEquals("Hola", resp.getMessage());
  }

  @Test
  public void testInvalidMessageHandlerStream(TestContext should) {

    List<Buffer> messages = Arrays.asList(
      Buffer.buffer(Request.newBuilder().setName("msg1").build().toByteArray()),
      Buffer.buffer(Request.newBuilder().setName("msg2-invalid").build().toByteArray()),
      Buffer.buffer(Request.newBuilder().setName("msg3").build().toByteArray()),
      Buffer.buffer(new byte[]{ 0,1,2,3,4,5,6,7 }),
      Buffer.buffer(Request.newBuilder().setName("msg5").build().toByteArray())
    );

    int invalidLen = messages.get(1).length() - 1;

    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(invalidLen - 1)).callHandler(SINK, call -> {
      List<Object> received = new ArrayList<>();
      call.invalidMessageHandler(received::add);
      call.handler(received::add);
      call.endHandler(v -> {
        should.assertEquals(Request.class, received.get(0).getClass());
        should.assertEquals(MessageSizeOverflowException.class, received.get(1).getClass());
        should.assertEquals(Request.class, received.get(2).getClass());
        should.assertEquals(InvalidMessagePayloadException.class, received.get(3).getClass());
        should.assertEquals(Request.class, received.get(4).getClass());
        should.assertEquals(5, received.size());
        call.response().end(Empty.getDefaultInstance());
      });
    }));

    Async test = should.async();

    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false)
    );

    client.request(HttpMethod.POST, 8080, "localhost", "/" + TestServiceGrpc.SERVICE_NAME + "/Sink")
      .onComplete(should.asyncAssertSuccess(request -> {
        request.putHeader(GrpcHeaderNames.GRPC_ENCODING, "gzip");
        request.setChunked(true);
        messages.forEach(msg -> {
          Buffer buffer = Buffer.buffer();
          buffer.appendByte((byte) 0); // Uncompressed
          buffer.appendInt(msg.length());
          buffer.appendBuffer(msg);
          request.write(buffer);
        });
        request.end();
        request.response().onComplete(should.asyncAssertSuccess(response -> {
          response.end().onComplete(should.asyncAssertSuccess(v -> {
            test.complete();
          }));
        }));
      }));

    test.awaitSuccess(20_000);
  }
}
