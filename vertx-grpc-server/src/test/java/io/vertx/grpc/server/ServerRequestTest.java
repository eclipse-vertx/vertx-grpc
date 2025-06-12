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
package io.vertx.grpc.server;

import io.grpc.*;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.streaming.Empty;
import io.grpc.examples.streaming.Item;
import io.grpc.examples.streaming.StreamingGrpc;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.InvalidMessagePayloadException;
import io.vertx.grpc.common.MessageSizeOverflowException;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerRequestTest extends ServerTest {

  @Override
  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) {
    startServer(GrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        if (!requestEncoding.equals("identity")) {
          should.assertEquals(requestEncoding, call.encoding());
        }
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
      .setPemKeyCertOptions(cert.keyCertOptions()), GrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
        response
          .end(helloReply);
      });
    }));

    ChannelCredentials creds = TlsChannelCredentials.newBuilder().trustManager(new File(cert.certificatePath())).build();
    channel = Grpc.newChannelBuilderForAddress("localhost", 8443, creds).build();
    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
    HelloRequest request = HelloRequest.newBuilder().setName("Julien").build();
    HelloReply res = stub.sayHello(request);
    should.assertEquals("Hello Julien", res.getMessage());
  }

  @Override
  public void testStatus(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      call.handler(helloRequest -> {
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
        response
          .status(GrpcStatus.UNAVAILABLE)
          .end();
      });
    }));

    super.testStatus(should);
  }

  @Override
  public void testServerStreaming(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getSourceMethod(), call -> {
      for (int i = 0; i < NUM_ITEMS; i++) {
        Item item = Item.newBuilder().setValue("the-value-" + i).build();
        call.response().write(item);
      }
      call.response().end();
    }));

    super.testServerStreaming(should);
  }

  @Override
  public void testClientStreaming(TestContext should) throws Exception {

    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getSinkMethod(), call -> {
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

    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getSinkMethod(), call -> {
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

    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getPipeMethod(), call -> {
      call.handler(item -> {
        call.response().write(item);
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
    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getPipeMethod(), call -> {
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

    startServer(GrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      should.assertEquals(0, testMetadataStep.getAndIncrement());
      MultiMap headers = call.headers();
      should.assertEquals("custom_request_header_value", headers.get("custom_request_header"));
      assertEquals(should, new byte[]{ 0,1,2 }, headers.get("custom_request_header-bin"));
      should.assertEquals("grpc-custom_request_header_value", headers.get("grpc-custom_request_header"));
      assertEquals(should, new byte[] { 2,1,0 }, headers.get("grpc-custom_request_header-bin"));
      call.handler(helloRequest -> {
        should.assertEquals(1, testMetadataStep.getAndAdd(2));
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
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
    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getPipeMethod(), call -> {
      call.handler(item -> {
        for (int i = 0;i < numMsg;i++) {
          call.response().write(item);
        }
        call.response().status(GrpcStatus.UNAVAILABLE).end();
      });
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    StreamingGrpc.StreamingStub stub = StreamingGrpc.newStub(channel);

    Async done = should.async();
    ClientCallStreamObserver<Item> items = (ClientCallStreamObserver<Item>) stub.pipe(new StreamObserver<Item>() {
      AtomicInteger count = new AtomicInteger();
      @Override
      public void onNext(Item value) {
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
    items.onNext(Item.newBuilder().setValue("the-value").build());
  }

  @Test
  public void testHandleCancel(TestContext should) {

    Async test = should.async();
    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getPipeMethod(), call -> {
      call.errorHandler(error -> {
        should.assertEquals(GrpcError.CANCELLED, error);
        test.complete();
      });
      call.handler(item -> {
        call.response().write(item);
      });
    }));

    super.testHandleCancel(should);
  }

  @Ignore
  @Test
  public void testEarlyHeadersOk(TestContext should) {
    testEarlyHeaders(GrpcStatus.OK, should);
  }

  @Ignore
  @Test
  public void testEarlyHeadersInvalidArgument(TestContext should) {
    testEarlyHeaders(GrpcStatus.INVALID_ARGUMENT, should);
  }

  private void testEarlyHeaders(GrpcStatus status, TestContext should) {

    AtomicReference<Runnable> continuation = new AtomicReference<>();

    startServer(GrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      GrpcServerResponse<HelloRequest, HelloReply> response = call.response();

      MultiMap headers = response.headers();

      headers.set("xx-acme-header", "whatever");

      continuation.set(() -> {
        response.status(status);
        response.end(HelloReply.newBuilder().setMessage("the message").build());
      });

//      response.writeHead();
    }));

    super.testEarlyHeaders(should, status, () -> continuation.get().run());
  }

  @Override
  public void testTrailersOnly(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      call.handler(helloRequest -> {
        GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
        response
          .status(GrpcStatus.INVALID_ARGUMENT)
          .end();
      });
    }));

    super.testTrailersOnly(should);
  }

  @Override
  public void testDistinctHeadersAndTrailers(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(StreamingGrpc.getSourceMethod(), call -> {
      call.handler(helloRequest -> {
        GrpcServerResponse<Empty, Item> response = call.response();
        response.end();
      });
    }));

    super.testDistinctHeadersAndTrailers(should);
  }

  @Test
  public void testCancel(TestContext should) {

    Async test = should.async();

    startServer(GrpcServer.server(vertx).callHandler(GreeterGrpc.getSayHelloMethod(), call -> {
      GrpcServerResponse<HelloRequest, HelloReply> response = call.response();
      response.cancel();
      try {
        response.write(HelloReply.newBuilder().build());
      } catch (IllegalStateException e) {
        test.complete();
      }
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

    HelloRequest request = HelloRequest.newBuilder().setName("Julien").build();
    try {
      stub.sayHello(request);
    } catch (StatusRuntimeException ignore) {
      should.assertEquals(Status.CANCELLED.getCode(), ignore.getStatus().getCode());
    }
  }

  @Test
  public void testDefaultMessageSizeOverflow(TestContext should) {

    HelloRequest request = HelloRequest.newBuilder().setName("Asmoranomardicadaistinaculdacar").build();
    int requestLen = request.getSerializedSize();

    MethodDescriptor<HelloRequest, HelloReply> method = GreeterGrpc.getSayHelloMethod();

    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(requestLen - 1))
      .callHandler(method, call -> {
      }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

    try {
      stub.sayHello(request);
      should.fail();
    } catch (StatusRuntimeException ignore) {
      should.assertEquals(Status.RESOURCE_EXHAUSTED.getCode(), ignore.getStatus().getCode());
    }
  }

  @Test
  public void testInvalidMessageHandler(TestContext should) {

    HelloRequest request = HelloRequest.newBuilder().setName("Asmoranomardicadaistinaculdacar").build();
    int requestLen = request.getSerializedSize();

    MethodDescriptor<HelloRequest, HelloReply> method = GreeterGrpc.getSayHelloMethod();

    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(requestLen - 1))
      .callHandler(method, call -> {
      AtomicInteger invalid = new AtomicInteger();
      call.handler(msg -> {
        should.fail();
      });
      call.invalidMessageHandler(err -> {
        should.assertEquals(0, invalid.getAndIncrement());
      });
      call.endHandler(v -> {
        call.response().end(HelloReply.newBuilder().setMessage("Hola").build());
      });
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

    HelloReply resp = stub.sayHello(request);
    should.assertEquals("Hola", resp.getMessage());
  }

  @Test
  public void testInvalidMessageHandlerStream(TestContext should) {

    List<Buffer> messages = Arrays.asList(
      Buffer.buffer(Item.newBuilder().setValue("msg1").build().toByteArray()),
      Buffer.buffer(Item.newBuilder().setValue("msg2-invalid").build().toByteArray()),
      Buffer.buffer(Item.newBuilder().setValue("msg3").build().toByteArray()),
      Buffer.buffer(new byte[]{ 0,1,2,3,4,5,6,7 }),
      Buffer.buffer(Item.newBuilder().setValue("msg5").build().toByteArray())
    );

    int invalidLen = messages.get(1).length() - 1;

    startServer(GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(invalidLen - 1)).callHandler(StreamingGrpc.getSinkMethod(), call -> {
      List<Object> received = new ArrayList<>();
      call.invalidMessageHandler(received::add);
      call.handler(received::add);
      call.endHandler(v -> {
        should.assertEquals(Item.class, received.get(0).getClass());
        should.assertEquals(MessageSizeOverflowException.class, received.get(1).getClass());
        should.assertEquals(Item.class, received.get(2).getClass());
        should.assertEquals(InvalidMessagePayloadException.class, received.get(3).getClass());
        should.assertEquals(Item.class, received.get(4).getClass());
        should.assertEquals(5, received.size());
        call.response().end(Empty.getDefaultInstance());
      });
    }));

    Async test = should.async();

    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false)
    );

    client.request(HttpMethod.POST, 8080, "localhost", "/" + StreamingGrpc.SERVICE_NAME + "/Sink", should.asyncAssertSuccess(request -> {
      request.putHeader("grpc-encoding", "gzip");
      request.setChunked(true);
      messages.forEach(msg -> {
        Buffer buffer = Buffer.buffer();
        buffer.appendByte((byte)0); // Uncompressed
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
