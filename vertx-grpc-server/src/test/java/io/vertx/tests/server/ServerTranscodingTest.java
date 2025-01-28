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

package io.vertx.tests.server;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.transcoding.MethodTranscodingOptions;
import io.vertx.grpcweb.GrpcWebTesting.*;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Test;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.*;

/**
 * A test class for grpc transcoding.
 */
public class ServerTranscodingTest extends GrpcTestBase {

  public static GrpcMessageDecoder<Empty> EMPTY_DECODER = GrpcMessageDecoder.json(Empty::newBuilder);
  public static GrpcMessageEncoder<Empty> EMPTY_ENCODER = GrpcMessageEncoder.json();
  public static GrpcMessageDecoder<EchoRequest> ECHO_REQUEST_DECODER = GrpcMessageDecoder.json(EchoRequest::newBuilder);
  public static GrpcMessageEncoder<EchoResponse> ECHO_RESPONSE_ENCODER = GrpcMessageEncoder.json();

  public static final ServiceName TEST_SERVICE_NAME = ServiceName.create("io.vertx.grpcweb.TestService");
  public static final ServiceMethod<Empty, Empty> EMPTY_CALL = ServiceMethod.server(TEST_SERVICE_NAME, "EmptyCall", EMPTY_ENCODER, EMPTY_DECODER);
  public static final ServiceMethod<EchoRequest, EchoResponse> UNARY_CALL = ServiceMethod.server(TEST_SERVICE_NAME, "UnaryCall", ECHO_RESPONSE_ENCODER, ECHO_REQUEST_DECODER);

  public static final MethodTranscodingOptions EMPTY_TRANSCODING = new MethodTranscodingOptions("", HttpMethod.valueOf("POST"), "/hello", "", "", null);
  public static final MethodTranscodingOptions UNARY_TRANSCODING = new MethodTranscodingOptions("", HttpMethod.valueOf("GET"), "/hello", "", "", null);
  public static final MethodTranscodingOptions UNARY_TRANSCODING_WITH_PARAM = new MethodTranscodingOptions("", HttpMethod.valueOf("GET"), "/hello/{payload}", "", "", null);

  private static final String TEST_SERVICE = "/io.vertx.grpcweb.TestService";

  private static final CharSequence USER_AGENT = HttpHeaders.createOptimized("X-User-Agent");
  private static final String CONTENT_TYPE = "application/json";

  private static final MultiMap HEADERS = HttpHeaders.headers()
    .add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
    .add(HttpHeaders.USER_AGENT, USER_AGENT)
    .add(HttpHeaders.ACCEPT, CONTENT_TYPE);

  private static final Empty EMPTY_DEFAULT_INSTANCE = Empty.getDefaultInstance();

  private HttpClient httpClient;
  private HttpServer httpServer;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);
    httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port));
    GrpcServer grpcServer = GrpcServer.server(vertx, new GrpcServerOptions().setGrpcTranscodingEnabled(true));
    grpcServer.callHandlerWithTranscoding(EMPTY_CALL, request -> {
      copyHeaders(request.headers(), request.response().headers());
      copyTrailers(request.headers(), request.response().trailers());
      request.response().end(Empty.newBuilder().build());
    }, EMPTY_TRANSCODING);
    grpcServer.callHandlerWithTranscoding(UNARY_CALL, request -> {
      request.handler(requestMsg -> {
        GrpcServerResponse<EchoRequest, EchoResponse> response = request.response();
        copyHeaders(request.headers(), response.headers());
        copyTrailers(request.headers(), response.trailers());
        String payload = requestMsg.getPayload();
        if ("boom".equals(payload)) {
          response.trailers().set("x-error-trailer", "boom");
          response.status(GrpcStatus.INTERNAL).end();
        } else {
          EchoResponse responseMsg = EchoResponse.newBuilder()
            .setPayload(payload)
            .build();
          response.end(responseMsg);
        }
      });
    }, UNARY_TRANSCODING);
    grpcServer.callHandlerWithTranscoding(UNARY_CALL, request -> {
      request.handler(requestMsg -> {
        GrpcServerResponse<EchoRequest, EchoResponse> response = request.response();
        copyHeaders(request.headers(), response.headers());
        copyTrailers(request.headers(), response.trailers());
        String payload = requestMsg.getPayload();
        if ("boom".equals(payload)) {
          response.trailers().set("x-error-trailer", "boom");
          response.status(GrpcStatus.INTERNAL).end();
        } else {
          EchoResponse responseMsg = EchoResponse.newBuilder()
            .setPayload(payload)
            .build();
          response.end(responseMsg);
        }
      });
    }, UNARY_TRANSCODING_WITH_PARAM);
    httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(port)).requestHandler(grpcServer);
    httpServer.listen().onComplete(should.asyncAssertSuccess());
  }

  @Override
  public void tearDown(TestContext should) {
    httpServer.close().onComplete(should.asyncAssertSuccess());
    httpClient.close().onComplete(should.asyncAssertSuccess());
    super.tearDown(should);
  }

  static void copyHeaders(MultiMap src, MultiMap headers) {
    copyMetadata(src, headers, "x-header-text-key", "x-header-bin-key-bin");
  }

  static void copyTrailers(MultiMap src, MultiMap headers) {
    copyMetadata(src, headers, "x-trailer-text-key", "x-trailer-bin-key-bin");
  }

  public static void copyMetadata(MultiMap src, MultiMap dst, String... keys) {
    for (String key : keys) {
      if (src.contains(key)) {
        dst.set(key, src.get(key));
      }
    }
  }

  @Test
  public void testEmpty(TestContext should) {
    httpClient.request(HttpMethod.POST, "/hello").compose(req -> {
        req.headers().addAll(HEADERS);
        return req.send().compose(response -> response.body().map(response));
      })
      .onComplete(should.asyncAssertSuccess(response -> {
        should.verify(v -> {
          assertEquals(200, response.statusCode());
          MultiMap headers = response.headers();

          assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
          assertEquals(Integer.parseInt(headers.get(HttpHeaders.CONTENT_LENGTH)), response.body().result().length());

          JsonObject body = decodeBody(response.body().result());
          assertEquals(0, body.size());
        });
      }));
  }

  @Test
  public void testPayload(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/hello/" + payload).compose(req -> {
      req.headers().addAll(HEADERS);
      return req.send().compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      MultiMap headers = response.headers();
      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  @Test
  public void testPayloadWithQuery(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/hello?payload=" + payload).compose(req -> {
      req.headers().addAll(HEADERS);
      return req.send().compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      MultiMap headers = response.headers();
      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  @Test
  public void testPayloadWithBody(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/hello").compose(req -> {
      String body = encode(EchoRequest.newBuilder().setPayload("foobar").build()).toString();
      req.headers().addAll(HEADERS);
      req.headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()));
      return req.send(body).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      MultiMap headers = response.headers();
      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  private Buffer encode(Message message) {
    Buffer buffer = BufferInternal.buffer();
    try {
      String json = JsonFormat.printer().print(message);
      buffer.appendString(json);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
    return buffer;
  }

  private JsonObject decodeBody(Buffer body) {
    String json = body.toString();
    return new JsonObject(json);
  }
}
