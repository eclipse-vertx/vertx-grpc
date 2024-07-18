/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.server.web;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.iogrpc.server.IoGrpcServer;
import io.vertx.iogrpc.server.IoGrpcServiceBridge;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.grpc.common.impl.GrpcMessageImpl;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServiceBridge;
import io.vertx.grpcweb.GrpcWebTesting.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.*;

/**
 * Base class for gRPC-Web tests.
 */
public abstract class ServerTestBase extends GrpcTestBase {

  private static final String TEST_SERVICE = "/io.vertx.grpcweb.TestService";

  private static final CharSequence HEADER_TEXT_KEY = HttpHeaders.createOptimized("x-header-text-key");
  private static final CharSequence HEADER_TEXT_VALUE = HttpHeaders.createOptimized("header_text_value");
  private static final CharSequence HEADER_BIN_KEY = HttpHeaders.createOptimized("x-header-bin-key-bin");
  private static final CharSequence HEADER_BIN_VALUE = HttpHeaders.createOptimized(String.valueOf(0xabcdef));
  private static final CharSequence TRAILER_TEXT_KEY = HttpHeaders.createOptimized("x-trailer-text-key");
  private static final CharSequence TRAILER_TEXT_VALUE = HttpHeaders.createOptimized("trailer_text_value");
  private static final CharSequence TRAILER_BIN_KEY = HttpHeaders.createOptimized("x-trailer-bin-key-bin");
  private static final CharSequence TRAILER_BIN_VALUE = HttpHeaders.createOptimized(String.valueOf(0xfedcba));
  private static final CharSequence TRAILER_ERROR_KEY = HttpHeaders.createOptimized("x-error-trailer");

  private static final MultiMap METADATA = HttpHeaders.headers()
    .add(HEADER_TEXT_KEY, HEADER_TEXT_VALUE)
    .add(HEADER_BIN_KEY, HEADER_BIN_VALUE)
    .add(TRAILER_TEXT_KEY, TRAILER_TEXT_VALUE)
    .add(TRAILER_BIN_KEY, TRAILER_BIN_VALUE);

  protected static final CharSequence USER_AGENT = HttpHeaders.createOptimized("X-User-Agent");
  protected static final CharSequence GRPC_WEB_JAVASCRIPT_0_1 = HttpHeaders.createOptimized("grpc-web-javascript/0.1");
  protected static final CharSequence GRPC_WEB = HttpHeaders.createOptimized("X-Grpc-Web");
  protected static final CharSequence TRUE = HttpHeaders.createOptimized("1");

  private static final String GRPC_STATUS = "grpc-status";
  private static final String STATUS_OK = GRPC_STATUS + ":" + 0 + "\r\n";
  private static final String TRAILERS_AND_STATUS = TRAILER_TEXT_KEY + ":" + TRAILER_TEXT_VALUE + "\r\n" +
                                                    TRAILER_BIN_KEY + ":" + TRAILER_BIN_VALUE + "\r\n" +
                                                    STATUS_OK;

  private static final Empty EMPTY_DEFAULT_INSTANCE = Empty.getDefaultInstance();

  private static final int PREFIX_SIZE = 5;

  private HttpClient httpClient;
  private HttpServer httpServer;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);
    httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port));
    IoGrpcServer grpcServer = IoGrpcServer.server(vertx, new GrpcServerOptions().setGrpcWebEnabled(true));
    ServerServiceDefinition serviceDefinition = ServerInterceptors.intercept(new TestServiceImpl(), new Interceptor());
    IoGrpcServiceBridge.bridge(serviceDefinition).bind(grpcServer);
    httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(port)).requestHandler(grpcServer);
    httpServer.listen().onComplete(should.asyncAssertSuccess());
  }

  @Override
  public void tearDown(TestContext should) {
    httpServer.close().onComplete(should.asyncAssertSuccess());
    httpClient.close().onComplete(should.asyncAssertSuccess());
    super.tearDown(should);
  }

  protected abstract MultiMap requestHeaders();

  protected abstract CharSequence responseContentType();

  protected abstract Buffer decodeBody(Buffer buffer);

  @Test
  public void testEmpty(TestContext should) {
    httpClient.request(HttpMethod.POST, TEST_SERVICE + "/EmptyCall").compose(req -> {
      req.headers().addAll(requestHeaders());
      return req.send(encode(EMPTY_DEFAULT_INSTANCE)).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> {
      should.verify(v -> {

        assertEquals(200, response.statusCode());
        MultiMap headers = response.headers();
        assertTrue(headers.contains(CONTENT_TYPE, responseContentType(), true));

        Buffer body = decodeBody(response.body().result());
        int pos = 0;

        Buffer prefix = body.getBuffer(pos, PREFIX_SIZE);
        assertEquals(0x00, prefix.getUnsignedByte(0)); // Uncompressed message
        assertEquals(0, prefix.getInt(1));
        pos += PREFIX_SIZE;

        Buffer trailer = body.getBuffer(pos, body.length());
        assertEquals(0x80, trailer.getUnsignedByte(0)); // Uncompressed trailer
        int len = trailer.getInt(1);
        assertEquals(STATUS_OK, trailer.getBuffer(PREFIX_SIZE, PREFIX_SIZE + len).toString());

      });
    }));
  }

  @Test
  public void testEmptyWithMetadata(TestContext should) {
    httpClient.request(HttpMethod.POST, TEST_SERVICE + "/EmptyCall").compose(req -> {
      req.headers()
        .addAll(METADATA)
        .addAll(requestHeaders());
      return req.send(encode(EMPTY_DEFAULT_INSTANCE)).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> {
      should.verify(v -> {

        assertEquals(200, response.statusCode());
        MultiMap headers = response.headers();
        assertTrue(headers.contains(CONTENT_TYPE, responseContentType(), true));

        Buffer body = decodeBody(response.body().result());
        int pos = 0;

        Buffer prefix = body.getBuffer(pos, PREFIX_SIZE);
        assertEquals(0x00, prefix.getUnsignedByte(0)); // Uncompressed message
        assertEquals(0, prefix.getInt(1));
        pos += PREFIX_SIZE;

        Buffer trailer = body.getBuffer(pos, body.length());
        assertEquals(0x80, trailer.getUnsignedByte(0)); // Uncompressed trailer
        int len = trailer.getInt(1);
        assertEquals(TRAILERS_AND_STATUS, trailer.getBuffer(PREFIX_SIZE, PREFIX_SIZE + len).toString());

      });
    }));
  }

  @Test
  public void testSmallPayload(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.POST, TEST_SERVICE + "/UnaryCall").compose(req -> {
      req.headers().addAll(requestHeaders());
      EchoRequest echoRequest = EchoRequest.newBuilder().setPayload(payload).build();
      return req.send(encode(echoRequest)).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> {
      should.verify(v -> {

        assertEquals(200, response.statusCode());
        MultiMap headers = response.headers();
        assertTrue(headers.contains(CONTENT_TYPE, responseContentType(), true));

        Buffer body = decodeBody(response.body().result());
        int pos = 0;

        Buffer prefix = body.getBuffer(pos, PREFIX_SIZE);
        assertEquals(0x00, prefix.getUnsignedByte(0)); // Uncompressed message
        int len = prefix.getInt(1);
        pos += PREFIX_SIZE;

        EchoResponse echoResponse = parseEchoResponse(body.getBuffer(pos, pos + len));
        assertEquals(payload, echoResponse.getPayload());
        pos += len;

        Buffer trailer = body.getBuffer(pos, body.length());
        assertEquals(0x80, trailer.getUnsignedByte(0)); // Uncompressed trailer
        len = trailer.getInt(1);
        assertEquals(STATUS_OK, trailer.getBuffer(PREFIX_SIZE, PREFIX_SIZE + len).toString());

      });
    }));
  }

  @Test
  public void testLargePayloadWithMetadata(TestContext should) {
    Random rnd = ThreadLocalRandom.current();
    String payload = IntStream.range(0, 16 * 1024).mapToObj(i -> "foobar").collect(joining());
    httpClient.request(HttpMethod.POST, TEST_SERVICE + "/UnaryCall").compose(req -> {
      req.setChunked(true);
      req.headers()
        .addAll(METADATA)
        .addAll(requestHeaders());
      EchoRequest echoRequest = EchoRequest.newBuilder().setPayload(payload).build();
      Buffer buffer = encode(echoRequest);
      // Make sure the server will get blocks of arbitrary size
      int length = buffer.length();
      for (int pos = 0, written; pos < length; pos += written) {
        written = Math.min(length - pos, 128 + rnd.nextInt(129));
        req.write(buffer.getBuffer(pos, pos + written));
      }
      req.end();
      return req.response().compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> {
      should.verify(v -> {

        assertEquals(200, response.statusCode());
        MultiMap headers = response.headers();
        assertTrue(headers.contains(CONTENT_TYPE, responseContentType(), true));

        Buffer body = decodeBody(response.body().result());
        int pos = 0;

        Buffer prefix = body.getBuffer(pos, PREFIX_SIZE);
        assertEquals(0x00, prefix.getUnsignedByte(0)); // Uncompressed message
        int len = prefix.getInt(1);
        pos += PREFIX_SIZE;

        EchoResponse echoResponse = parseEchoResponse(body.getBuffer(pos, pos + len));
        assertEquals(payload, echoResponse.getPayload());
        pos += len;

        Buffer trailer = body.getBuffer(pos, body.length());
        assertEquals(0x80, trailer.getUnsignedByte(0)); // Uncompressed trailer
        len = trailer.getInt(1);
        assertEquals(TRAILERS_AND_STATUS, trailer.getBuffer(PREFIX_SIZE, PREFIX_SIZE + len).toString());

      });
    }));
  }

  @Test
  public void testServerSideStreaming(TestContext should) {
    List<Integer> requestedSizes = Arrays.asList(157, 52, 16 * 1024, 1);
    httpClient.request(HttpMethod.POST, TEST_SERVICE + "/StreamingCall").compose(req -> {
      req.headers().addAll(requestHeaders());
      StreamingRequest streamingRequest = StreamingRequest.newBuilder().addAllResponseSize(requestedSizes).build();
      return req.send(encode(streamingRequest)).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> {
      should.verify(v -> {

        assertEquals(200, response.statusCode());
        MultiMap headers = response.headers();
        assertTrue(headers.contains(CONTENT_TYPE, responseContentType(), true));

        Buffer body = decodeBody(response.body().result());
        int pos = 0;

        int len;
        for (int requestedSize : requestedSizes) {
          Buffer prefix = body.getBuffer(pos, pos + PREFIX_SIZE);
          pos += PREFIX_SIZE;
          assertEquals(0x00, prefix.getUnsignedByte(0)); // Uncompressed message
          len = prefix.getInt(1);
          StreamingResponse streamingResponse = parseStreamingResponse(body.getBuffer(pos, pos + len));
          char[] expected = new char[requestedSize];
          Arrays.fill(expected, 'a');
          assertArrayEquals(expected, streamingResponse.getPayload().toCharArray());
          pos += len;
        }

        Buffer trailer = body.getBuffer(pos, body.length());
        assertEquals(0x80, trailer.getUnsignedByte(0)); // Uncompressed trailer
        len = trailer.getInt(1);
        assertEquals(STATUS_OK, trailer.getBuffer(PREFIX_SIZE, PREFIX_SIZE + len).toString());

      });
    }));
  }

  @Test
  public void testServerSideStreamingWithMetadata(TestContext should) {
    List<Integer> requestedSizes = Arrays.asList(157, 52, 16 * 1024, 1);
    httpClient.request(HttpMethod.POST, TEST_SERVICE + "/StreamingCall").compose(req -> {
      req.headers()
        .addAll(METADATA)
        .addAll(requestHeaders());
      StreamingRequest streamingRequest = StreamingRequest.newBuilder().addAllResponseSize(requestedSizes).build();
      return req.send(encode(streamingRequest)).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> {
      should.verify(v -> {

        assertEquals(200, response.statusCode());
        MultiMap headers = response.headers();
        assertTrue(headers.contains(CONTENT_TYPE, responseContentType(), true));

        Buffer body = decodeBody(response.body().result());
        int pos = 0;

        int len;
        for (int requestedSize : requestedSizes) {
          Buffer prefix = body.getBuffer(pos, pos + PREFIX_SIZE);
          pos += PREFIX_SIZE;
          assertEquals(0x00, prefix.getUnsignedByte(0)); // Uncompressed message
          len = prefix.getInt(1);
          StreamingResponse streamingResponse = parseStreamingResponse(body.getBuffer(pos, pos + len));
          char[] expected = new char[requestedSize];
          Arrays.fill(expected, 'a');
          assertArrayEquals(expected, streamingResponse.getPayload().toCharArray());
          pos += len;
        }

        Buffer trailer = body.getBuffer(pos, body.length());
        assertEquals(0x80, trailer.getUnsignedByte(0)); // Uncompressed trailer
        len = trailer.getInt(1);
        assertEquals(TRAILERS_AND_STATUS, trailer.getBuffer(PREFIX_SIZE, PREFIX_SIZE + len).toString());

      });
    }));
  }

  @Test
  public void testTrailersOnly(TestContext should) {
    httpClient.request(HttpMethod.POST, TEST_SERVICE + "/UnaryCall").compose(req -> {
      req.headers()
        .addAll(METADATA)
        .addAll(requestHeaders());
      EchoRequest echoRequest = EchoRequest.newBuilder().setPayload("boom").build();
      return req.send(encode(echoRequest)).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> {
      should.verify(v -> {
        assertEquals(200, response.statusCode());
        MultiMap headers = response.headers();
        assertTrue(headers.contains(CONTENT_TYPE, responseContentType(), true));
        assertTrue(headers.contains(CONTENT_LENGTH, "0", true));
        assertTrue(headers.contains(TRAILER_TEXT_KEY, TRAILER_TEXT_VALUE, false));
        assertTrue(headers.contains(TRAILER_BIN_KEY, TRAILER_BIN_VALUE, false));
        assertTrue(headers.contains(TRAILER_ERROR_KEY, "boom", false));
        assertTrue(headers.contains(GRPC_STATUS, "13", false));
      });
    }));
  }

  protected Buffer encode(Message message) {
    return GrpcMessageImpl.encode(GrpcMessage.message("identity", Buffer.buffer(message.toByteArray())));
  }

  private static EchoResponse parseEchoResponse(Buffer buffer) {
    try {
      return EchoResponse.parseFrom(buffer.getBytes());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  private static StreamingResponse parseStreamingResponse(Buffer buffer) {
    try {
      return StreamingResponse.parseFrom(buffer.getBytes());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }
}
