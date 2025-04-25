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

package io.vertx.tests.transcoding;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.transcoding.MethodTranscodingOptions;
import io.vertx.grpc.transcoding.TranscodingServiceMethod;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.server.grpc.web.*;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * A test class for grpc transcoding.
 */
public class ServerTranscodingTest extends GrpcTestBase {

  public static MethodTranscodingOptions create(String selector, HttpMethod httpMethod, String path, String body, String responseBody, MethodTranscodingOptions... additionalBindings) {
    MethodTranscodingOptions ret = new MethodTranscodingOptions()
      .setSelector(selector)
      .setHttpMethod(httpMethod)
      .setPath(path).setBody(body).setResponseBody(responseBody);
    if (additionalBindings != null) {
      ret.getAdditionalBindings().addAll(Arrays.asList(additionalBindings));
    }
    return ret;
  }

  public static GrpcMessageDecoder<Empty> EMPTY_DECODER = GrpcMessageDecoder.decoder(Empty.newBuilder());
  public static GrpcMessageEncoder<Empty> EMPTY_ENCODER = GrpcMessageEncoder.encoder();
  public static GrpcMessageDecoder<EchoRequest> ECHO_REQUEST_DECODER = GrpcMessageDecoder.decoder(EchoRequest.newBuilder());
  public static GrpcMessageDecoder<EchoRequestBody> ECHO_REQUEST_BODY_DECODER = GrpcMessageDecoder.decoder(EchoRequestBody.newBuilder());
  public static GrpcMessageEncoder<EchoResponse> ECHO_RESPONSE_ENCODER = GrpcMessageEncoder.encoder();
  public static GrpcMessageEncoder<EchoResponseBody> ECHO_RESPONSE_BODY_ENCODER = GrpcMessageEncoder.encoder();

  public static final ServiceName TEST_SERVICE_NAME = ServiceName.create(TestServiceGrpc.SERVICE_NAME);

  public static final MethodTranscodingOptions EMPTY_TRANSCODING = new MethodTranscodingOptions().setHttpMethod(HttpMethod.POST).setPath("/hello");
  public static final MethodTranscodingOptions UNARY_TRANSCODING = new MethodTranscodingOptions().setPath("/hello");
  public static final MethodTranscodingOptions UNARY_TRANSCODING_WITH_PARAM = new MethodTranscodingOptions().setPath("/hello/{payload}");
  public static final MethodTranscodingOptions UNARY_TRANSCODING_WITH_CUSTOM_METHOD = new MethodTranscodingOptions().setHttpMethod(HttpMethod.valueOf("ACL")).setPath("/hello");
  public static final MethodTranscodingOptions UNARY_TRANSCODING_WITH_BODY = new MethodTranscodingOptions().setPath("/body").setBody("request");
  public static final MethodTranscodingOptions UNARY_TRANSCODING_WITH_RESPONSE_BODY = new MethodTranscodingOptions().setPath("/response").setResponseBody("response");

  public static final TranscodingServiceMethod<Empty, Empty> EMPTY_CALL = TranscodingServiceMethod.server(TEST_SERVICE_NAME, "EmptyCall", EMPTY_ENCODER, EMPTY_DECODER, EMPTY_TRANSCODING);
  public static final TranscodingServiceMethod<EchoRequest, EchoResponse> UNARY_CALL = TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCall", ECHO_RESPONSE_ENCODER, ECHO_REQUEST_DECODER, UNARY_TRANSCODING);
  public static final TranscodingServiceMethod<EchoRequest, EchoResponse> UNARY_CALL_WITH_PARAM = TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCallWithParam", ECHO_RESPONSE_ENCODER,
    ECHO_REQUEST_DECODER, UNARY_TRANSCODING_WITH_PARAM);
  public static final TranscodingServiceMethod<EchoRequest, EchoResponse> UNARY_CALL_WITH_CUSTOM_METHOD = TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCallWithCustomMethod",
    ECHO_RESPONSE_ENCODER,
    ECHO_REQUEST_DECODER, UNARY_TRANSCODING_WITH_CUSTOM_METHOD);

  public static final TranscodingServiceMethod<EchoRequestBody, EchoResponse> UNARY_CALL_WITH_BODY = TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCallWithBody", ECHO_RESPONSE_ENCODER,
    ECHO_REQUEST_BODY_DECODER, UNARY_TRANSCODING_WITH_BODY);

  public static final TranscodingServiceMethod<EchoRequest, EchoResponseBody> UNARY_CALL_WITH_RESPONSE_BODY = TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCallWithResponseBody",
    ECHO_RESPONSE_BODY_ENCODER, ECHO_REQUEST_DECODER, UNARY_TRANSCODING_WITH_RESPONSE_BODY);

  private static final CharSequence USER_AGENT = HttpHeaders.createOptimized("X-User-Agent");
  private static final String CONTENT_TYPE = "application/json";

  private static final MultiMap HEADERS = HttpHeaders.headers()
    .add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
    .add(HttpHeaders.USER_AGENT, USER_AGENT)
    .add(HttpHeaders.ACCEPT, CONTENT_TYPE)
    .copy(false);

  private static final Empty EMPTY_DEFAULT_INSTANCE = Empty.getDefaultInstance();

  private HttpClient httpClient;
  private HttpServer httpServer;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);
    httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port).setProtocolVersion(HttpVersion.HTTP_2));
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.callHandler(EMPTY_CALL, request -> {
      copyHeaders(request.headers(), request.response().headers());
      copyTrailers(request.headers(), request.response().trailers());
      request.response().end(Empty.newBuilder().build());
    });
    grpcServer.callHandler(UNARY_CALL, request -> {
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
    });
    grpcServer.callHandler(UNARY_CALL_WITH_PARAM, request -> {
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
    });
    grpcServer.callHandler(UNARY_CALL_WITH_CUSTOM_METHOD, request -> {
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
    });
    grpcServer.callHandler(UNARY_CALL_WITH_BODY, request -> {
      request.handler(requestMsg -> {
        GrpcServerResponse<EchoRequestBody, EchoResponse> response = request.response();
        copyHeaders(request.headers(), response.headers());
        copyTrailers(request.headers(), response.trailers());
        String payload = requestMsg.getRequest().getPayload();
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
    });
    grpcServer.callHandler(UNARY_CALL_WITH_RESPONSE_BODY, request -> {
      request.handler(requestMsg -> {
        GrpcServerResponse<EchoRequest, EchoResponseBody> response = request.response();
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
          EchoResponseBody responseBody = EchoResponseBody.newBuilder()
            .setResponse(responseMsg)
            .build();
          response.end(responseBody);
        }
      });
    });
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

  @Test
  public void testNestedQueryBody(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/body?request.payload=" + payload).compose(req -> {
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
  public void testInvalidPayload(TestContext should) {
    String payload = "boom";
    httpClient.request(HttpMethod.GET, "/hello").compose(req -> {
      req.headers().addAll(HEADERS);
      return req.send(payload).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(400, response.statusCode());
      MultiMap headers = response.headers();
      //      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
    })));
  }

  @Test
  public void testCustomMethod(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.ACL, "/hello").compose(req -> {
      String body = encode(EchoRequest.newBuilder().setPayload(payload).build()).toString();
      req.headers().addAll(HEADERS);
      return req.send(body).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      MultiMap headers = response.headers();
      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  @Test
  public void testMethodWithRequestBody(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/body").compose(req -> {
      String body = encode(EchoRequest.newBuilder().setPayload(payload).build()).toString();
      req.headers().addAll(HEADERS);
      return req.send(body).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      MultiMap headers = response.headers();
      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  @Test
  public void testMethodWithErrorRequestBody(TestContext should) {
    String payload = "boom";
    httpClient.request(HttpMethod.GET, "/body").compose(req -> {
      String body = encode(EchoRequest.newBuilder().setPayload(payload).build()).toString();
      req.headers().addAll(HEADERS);
      return req.send(body).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(500, response.statusCode());
      MultiMap headers = response.headers();
      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
    })));
  }

  @Test
  public void testMethodWithMalformedRequestBody(TestContext should) {
    String payload = "malformatedbody";
    httpClient.request(HttpMethod.GET, "/body").compose(req -> {
      req.headers().addAll(HEADERS);
      return req.send(payload).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(400, response.statusCode());
      MultiMap headers = response.headers();
      //      assertTrue(headers.contains(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE, true));
    })));
  }

  @Test
  public void testMethodWithResponseBody(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/response").compose(req -> {
      String body = encode(EchoRequest.newBuilder().setPayload(payload).build()).toString();
      req.headers().addAll(HEADERS);
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

  @Test
  public void testHttp2Proto() {
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceBlockingStub client = TestServiceGrpc.newBlockingStub(channel);
    EchoResponse response = client.unaryCall(EchoRequest.newBuilder().setPayload("hello").build());
    assertEquals("hello", response.getPayload());
  }
}
