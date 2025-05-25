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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ServerMessageEncodingTest extends ServerTestBase {

  private HttpClient client;

  @Test
  public void testZipResponseCompress(TestContext should) {
    testEncode(should, "gzip", GrpcMessage.message("identity", Buffer.buffer("Hello World")), true);
  }

  @Test
  public void testZipResponsePassThrough(TestContext should) {
    testEncode(should, "gzip", GrpcMessage.message("gzip", GrpcTestBase.zip(Buffer.buffer("Hello World"))), true);
  }

  @Test
  public void testIdentityResponseUnzip(TestContext should) {
    testEncode(should, "identity", GrpcMessage.message("gzip", GrpcTestBase.zip(Buffer.buffer("Hello World"))), false);
  }

  @Test
  public void testIdentityRequestPassThrough(TestContext should) {
    testEncode(should, "identity", GrpcMessage.message("identity", Buffer.buffer("Hello World")), false);
  }

  @Test
  public void testSnappyResponseCompress(TestContext should) {
    testEncode(should, "snappy", GrpcMessage.message("identity", Buffer.buffer("Hello World")), true);
  }

  @Test
  public void testSnappyResponsePassThrough(TestContext should) {
    testEncode(should, "snappy", GrpcMessage.message("snappy", GrpcTestBase.snappyCompress(Buffer.buffer("Hello World"))), true);
  }

  @Test
  public void testIdentityResponseUnsnappy(TestContext should) {
    testEncode(should, "identity", GrpcMessage.message("snappy", GrpcTestBase.snappyCompress(Buffer.buffer("Hello World"))), false);
  }

  private void testEncode(TestContext should, String encoding, GrpcMessage msg, boolean compressed) {

    Buffer expected = Buffer.buffer("Hello World");

    startServer(GrpcServer.server(vertx).callHandler(call -> {
      call.handler(request -> {
        GrpcServerResponse<Buffer, Buffer> response = call.response();
        response
          .encoding(encoding)
          .endMessage(msg);
      });
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );

    Async done = should.async();

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .onComplete(should.asyncAssertSuccess(request -> {
      request.putHeader(GrpcHeaderNames.GRPC_ENCODING, "identity");
      request.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
      request.send(Buffer
        .buffer()
        .appendByte((byte)1)
        .appendInt(expected.length())
        .appendBuffer(expected)).onComplete(should.asyncAssertSuccess(resp -> {
          resp.body().onComplete(should.asyncAssertSuccess(body -> {
            should.assertEquals(compressed ? 1 : 0, (int)body.getByte(0));
            int len = body.getInt(1);
            Buffer received = body.slice(5, 5 + len);
            if (compressed) {
              if (encoding.equals("gzip")) {
                received = GrpcTestBase.unzip(received);
              } else if (encoding.equals("snappy")) {
                received = GrpcTestBase.snappyDecompress(received);
              }
            }
            should.assertEquals(expected, received);
            done.complete();
          }));
      }));
    }));
  }

  @Test
  public void testEncodeError(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(call -> {
      call.handler(request -> {
        GrpcServerResponse<Buffer, Buffer> response = call.response();
        List<GrpcMessage> messages = Arrays.asList(
          GrpcMessage.message("gzip", Buffer.buffer("Hello World")),
          GrpcMessage.message("gzip", Buffer.buffer("not-gzip")),
          GrpcMessage.message("unknown", Buffer.buffer("unknown"))
        );
        response
          .encoding("identity");
        for (GrpcMessage message : messages) {
          Future<Void> fut = response.writeMessage(message);
          fut.onComplete(should.asyncAssertFailure());
        }
        response.cancel();
      });
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Request request = Request.newBuilder().setName("Julien").build();
    try {
      stub.unary(request);
    } catch (StatusRuntimeException ignore) {
    }
  }

  @Test
  public void testDecodeMessageHandler(TestContext should) {
    testDecode(should, GrpcTestBase.zip(Buffer.buffer("Hello World")), callResponse -> {
      AtomicInteger count = new AtomicInteger();
      callResponse.messageHandler(msg -> {
        should.assertEquals("gzip", msg.encoding());
        should.assertEquals(Buffer.buffer("Hello World"), GrpcTestBase.unzip(msg.payload()));
        count.incrementAndGet();
      });
      callResponse.endHandler(v -> {
        should.assertEquals(1, count.get());
        callResponse.response().end();
      });
    }, req -> req.response().onComplete(should.asyncAssertSuccess()));
  }

  @Test
  public void testDecodeHandler(TestContext should) {
    testDecode(should, GrpcTestBase.zip(Buffer.buffer("Hello World")), callResponse -> {
      AtomicInteger count = new AtomicInteger();
      callResponse.handler(msg -> {
        should.assertEquals(Buffer.buffer("Hello World"), msg);
        count.incrementAndGet();
      });
      callResponse.endHandler(v -> {
        should.assertEquals(1, count.get());
        callResponse.response().end();
      });
    }, req -> req.response().onComplete(should.asyncAssertSuccess()));
  }

  @Test
  public void testDecodeError(TestContext should) {
    testDecode(should, Buffer.buffer("Hello World"), req -> {
      req.handler(msg -> {
        should.fail();
      });
    }, req -> req.response().onComplete(should.asyncAssertFailure(err -> {
      should.assertEquals(StreamResetException.class, err.getClass());
      StreamResetException reset = (StreamResetException) err;
      should.assertEquals(GrpcError.CANCELLED.http2ResetCode, reset.getCode());
    })));
  }

  private void testDecode(TestContext should, Buffer payload, Consumer<GrpcServerRequest<Buffer, Buffer>> impl, Consumer<HttpClientRequest> checker) {

    startServer(GrpcServer.server(vertx).callHandler(call -> {
      should.assertEquals("gzip", call.encoding());
      impl.accept(call);
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false)
    );

    client.request(HttpMethod.POST, 8080, "localhost", "/").onComplete( should.asyncAssertSuccess(request -> {
      request.putHeader(GrpcHeaderNames.GRPC_ENCODING, "gzip");
      request.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
      request.end(Buffer
        .buffer()
        .appendByte((byte)1)
        .appendInt(payload.length())
        .appendBuffer(payload));
      checker.accept(request);
    }));
  }

  // A test to check, gRPC implementation behavior
  @Test
  public void testClientDecodingError(TestContext should) throws Exception {

    Async done = should.async();

    vertx.createHttpServer().requestHandler(req -> {
        req.response()
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc")
          .putHeader(GrpcHeaderNames.GRPC_ENCODING, "gzip")
          .write(Buffer.buffer()
            .appendByte((byte) 1)
            .appendInt(11)
            .appendString("Hello World"));
        req.response().exceptionHandler(err -> {
          if (err instanceof StreamResetException) {
            StreamResetException reset = (StreamResetException) err;
            should.assertEquals(GrpcError.CANCELLED.http2ResetCode, reset.getCode());
            done.complete();
          }
        });
    }).listen(8080, "localhost")
      .toCompletionStage()
      .toCompletableFuture()
      .get(20, TimeUnit.SECONDS);

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    AtomicReference<String> responseGrpcEncoding = new AtomicReference<>();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel, new ClientInterceptor() {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
          return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
              super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                @Override
                public void onHeaders(Metadata headers) {
                  responseGrpcEncoding.set(headers.get(Metadata.Key.of("grpc-encoding", io.grpc.Metadata.ASCII_STRING_MARSHALLER)));
                  super.onHeaders(headers);
                }
              }, headers);
            }
          };
        }
      }));
    Request request = Request.newBuilder().setName("Julien").build();
    try {
      stub.unary(request);
    } catch (StatusRuntimeException ignore) {
    }
  }

  /**
   * Test case 1: When a compression level is not specified for either the channel or the message,
   * the default channel level none is considered: data MUST NOT be compressed.
   *
   * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC Compression Test Cases</a>
   */
  @Test
  public void testNoCompressionSpecified(TestContext should) {
    Buffer expected = Buffer.buffer("Hello World");

    startServer(GrpcServer.server(vertx).callHandler(call -> {
      call.handler(request -> {
        GrpcServerResponse<Buffer, Buffer> response = call.response();
        // No encoding specified, should default to identity (no compression)
        response.endMessage(GrpcMessage.message("identity", expected));
      });
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );

    Async done = should.async();

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .onComplete(should.asyncAssertSuccess(request -> {
        // No encoding specified in request
        request.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        request.send(Buffer
          .buffer()
          .appendByte((byte)0) // Not compressed
          .appendInt(expected.length())
          .appendBuffer(expected)).onComplete(should.asyncAssertSuccess(resp -> {
            resp.body().onComplete(should.asyncAssertSuccess(body -> {
              // Verify message is not compressed (flag is 0)
              should.assertEquals(0, (int)body.getByte(0));
              int len = body.getInt(1);
              Buffer received = body.slice(5, 5 + len);
              should.assertEquals(expected, received);
              done.complete();
            }));
        }));
      }));
  }

  /**
   * Test case 2: When per-RPC compression configuration isn't present for a message,
   * the channel compression configuration MUST be used.
   *
   * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC Compression Test Cases</a>
   */
  @Test
  public void testChannelCompressionUsed(TestContext should) {
    Buffer expected = Buffer.buffer("Hello World");

    startServer(GrpcServer.server(vertx).callHandler(call -> {
      call.handler(request -> {
        GrpcServerResponse<Buffer, Buffer> response = call.response();
        // Set channel encoding to gzip
        response.encoding("gzip");
        // Send message without specifying encoding - should use channel encoding (gzip)
        response.endMessage(GrpcMessage.message("identity", expected));
      });
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );

    Async done = should.async();

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .onComplete(should.asyncAssertSuccess(request -> {
        // Accept gzip encoding
        request.putHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING, "gzip");
        request.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        request.send(Buffer
          .buffer()
          .appendByte((byte)0) // Not compressed
          .appendInt(expected.length())
          .appendBuffer(expected)).onComplete(should.asyncAssertSuccess(resp -> {
            resp.body().onComplete(should.asyncAssertSuccess(body -> {
              // Verify message is compressed (flag is 1)
              should.assertEquals(1, (int)body.getByte(0));
              int len = body.getInt(1);
              Buffer received = body.slice(5, 5 + len);
              // Decompress the received data
              received = GrpcTestBase.unzip(received);
              should.assertEquals(expected, received);
              done.complete();
            }));
        }));
      }));
  }

  /**
   * Test case 3: When a compression method (including no compression) is specified for an outgoing message,
   * the message MUST be compressed accordingly.
   *
   * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC Compression Test Cases</a>
   */
  @Test
  public void testSpecifiedCompressionUsed(TestContext should) {
    Buffer expected = Buffer.buffer("Hello World");

    startServer(GrpcServer.server(vertx).callHandler(call -> {
      call.handler(request -> {
        GrpcServerResponse<Buffer, Buffer> response = call.response();
        // Set channel encoding to identity (no compression)
        response.encoding("identity");
        // Send message with specific encoding (gzip) - should override channel encoding
        response.endMessage(GrpcMessage.message("gzip", GrpcTestBase.zip(expected)));
      });
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );

    Async done = should.async();

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .onComplete(should.asyncAssertSuccess(request -> {
        // Accept gzip encoding
        request.putHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING, "gzip");
        request.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        request.send(Buffer
          .buffer()
          .appendByte((byte)0) // Not compressed
          .appendInt(expected.length())
          .appendBuffer(expected)).onComplete(should.asyncAssertSuccess(resp -> {
            resp.body().onComplete(should.asyncAssertSuccess(body -> {
              // Verify message is compressed (flag is 1)
              should.assertEquals(1, (int)body.getByte(0));
              int len = body.getInt(1);
              Buffer received = body.slice(5, 5 + len);
              // Decompress the received data
              received = GrpcTestBase.unzip(received);
              should.assertEquals(expected, received);
              done.complete();
            }));
        }));
      }));
  }

  /**
   * Test case 4: A message compressed by a client in a way not supported by its server MUST fail with status UNIMPLEMENTED,
   * its associated description indicating the unsupported condition as well as the supported ones.
   * The returned grpc-accept-encoding header MUST NOT contain the compression method (encoding) used.
   *
   * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC Compression Test Cases</a>
   */
  @Test
  public void testClientUnsupportedCompression(TestContext should) {
    Buffer expected = Buffer.buffer("Hello World");
    String unsupportedEncoding = "unsupported-compression";

    // Server only accepts gzip and identity
    startServer(GrpcServer.server(vertx).callHandler(call -> {
      should.fail("Server should not process the request with unsupported compression");
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );

    Async done = should.async();

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .onComplete(should.asyncAssertSuccess(request -> {
        // Use unsupported compression
        request.putHeader(GrpcHeaderNames.GRPC_ENCODING, unsupportedEncoding);
        request.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        request.send(Buffer
          .buffer()
          .appendByte((byte)1) // Compressed flag set
          .appendInt(expected.length())
          .appendBuffer(expected)).onComplete(should.asyncAssertSuccess(resp -> {
            // Should get UNIMPLEMENTED status
            should.assertEquals("12", resp.getHeader(GrpcHeaderNames.GRPC_STATUS));

            // Check that the response doesn't include the unsupported encoding in accept-encoding
            String acceptEncoding = resp.getHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING);
            should.assertNotNull(acceptEncoding);
            should.assertFalse(acceptEncoding.contains(unsupportedEncoding));

            done.complete();
        }));
      }));
  }

  /**
   * Test case 5: A message compressed by a server in a way not supported by its client MUST fail with status INTERNAL,
   * its associated description indicating the unsupported condition as well as the supported ones.
   * The returned grpc-accept-encoding header MUST NOT contain the compression method (encoding) used.
   *
   * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC Compression Test Cases</a>
   */
  @Test
  public void testServerUnsupportedCompression(TestContext should) throws Exception {
    String unsupportedEncoding = "unsupported-compression";

    Async done = should.async();

    // Create a server that uses unsupported compression
    vertx.createHttpServer().requestHandler(req -> {
      req.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc")
        .putHeader(GrpcHeaderNames.GRPC_ENCODING, unsupportedEncoding)
        .write(Buffer.buffer()
          .appendByte((byte) 1) // Compressed flag set
          .appendInt(11)
          .appendString("Hello World"));
      req.response().exceptionHandler(err -> {
        if (err instanceof StreamResetException) {
          StreamResetException reset = (StreamResetException) err;
          // Client should reset with INTERNAL error
          should.assertEquals(GrpcError.INTERNAL.http2ResetCode, reset.getCode());
          done.complete();
        }
      });
    }).listen(8080, "localhost")
      .toCompletionStage()
      .toCompletableFuture()
      .get(20, TimeUnit.SECONDS);

    // Create a client that only accepts gzip and identity
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    AtomicReference<String> responseGrpcEncoding = new AtomicReference<>();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel, new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            // Only accept gzip and identity
            headers.put(Metadata.Key.of("grpc-accept-encoding", Metadata.ASCII_STRING_MARSHALLER), "gzip,identity");
            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onHeaders(Metadata headers) {
                responseGrpcEncoding.set(headers.get(Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER)));
                super.onHeaders(headers);
              }
            }, headers);
          }
        };
      }
    }));

    Request request = Request.newBuilder().setName("Julien").build();
    try {
      stub.unary(request);
      should.fail("Expected exception due to unsupported compression");
    } catch (StatusRuntimeException e) {
      // Should get INTERNAL status
      should.assertEquals(Status.INTERNAL.getCode(), e.getStatus().getCode());
      // Description should mention the unsupported encoding
      should.assertTrue(e.getStatus().getDescription().contains(unsupportedEncoding));
    }
  }

  /**
   * Test case 6: An ill-constructed message with its Compressed-Flag bit set but lacking a grpc-encoding entry
   * different from identity in its metadata MUST fail with INTERNAL status,
   * its associated description indicating the invalid Compressed-Flag condition.
   *
   * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC Compression Test Cases</a>
   */
  @Test
  public void testIllConstructedMessage(TestContext should) {
    Buffer expected = Buffer.buffer("Hello World");

    // Server should detect the ill-constructed message
    startServer(GrpcServer.server(vertx).callHandler(call -> {
      should.fail("Server should not process the ill-constructed message");
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );

    Async done = should.async();

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .onComplete(should.asyncAssertSuccess(request -> {
        // Set identity encoding (which means no compression)
        request.putHeader(GrpcHeaderNames.GRPC_ENCODING, "identity");
        request.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        // But set the compressed flag to 1, which is inconsistent with identity encoding
        request.send(Buffer
          .buffer()
          .appendByte((byte)1) // Compressed flag set incorrectly
          .appendInt(expected.length())
          .appendBuffer(expected)).onComplete(should.asyncAssertSuccess(resp -> {
            // Should get INTERNAL status
            should.assertEquals("13", resp.getHeader(GrpcHeaderNames.GRPC_STATUS));

            // Message should indicate the invalid compressed flag condition
            String errorMessage = resp.getHeader(GrpcHeaderNames.GRPC_MESSAGE);
            should.assertNotNull(errorMessage);
            should.assertTrue(errorMessage.contains("Compressed-Flag") ||
                             errorMessage.contains("compressed flag") ||
                             errorMessage.contains("compression"));

            done.complete();
        }));
      }));
  }
}
