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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.stub.StreamObserver;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.transcoding.MethodTranscodingOptions;
import io.vertx.grpc.transcoding.TranscodingServiceMethod;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.server.grpc.web.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ServerBridgeTranscodingTest extends GrpcTestBase {

  private static final String CONTENT_TYPE = "application/json";

  private static final ServiceName TEST_SERVICE_NAME = ServiceName.create(TestServiceGrpc.SERVICE_NAME);

  private static final GrpcMessageDecoder<EchoRequest> ECHO_REQUEST_DECODER = GrpcMessageDecoder.decoder(EchoRequest.newBuilder());
  private static final GrpcMessageEncoder<EchoResponse> ECHO_RESPONSE_ENCODER = GrpcMessageEncoder.encoder();
  private static final GrpcMessageDecoder<Empty> EMPTY_DECODER = GrpcMessageDecoder.decoder(Empty.newBuilder());
  private static final GrpcMessageEncoder<Empty> EMPTY_ENCODER = GrpcMessageEncoder.encoder();

  private static final TranscodingServiceMethod<EchoRequest, EchoResponse> UNARY_CALL_WITH_PARAM =
    TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCall", ECHO_RESPONSE_ENCODER, ECHO_REQUEST_DECODER,
      new MethodTranscodingOptions().setPath("/bridge/hello/{payload}"));

  private static final TranscodingServiceMethod<EchoRequest, EchoResponse> UNARY_CALL_WITH_BODY =
    TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCall", ECHO_RESPONSE_ENCODER, ECHO_REQUEST_DECODER,
      new MethodTranscodingOptions().setHttpMethod(HttpMethod.POST).setPath("/bridge/hello").setBody("*"));

  private static final TranscodingServiceMethod<EchoRequest, EchoResponse> UNARY_CALL_WITH_QUERY =
    TranscodingServiceMethod.server(TEST_SERVICE_NAME, "UnaryCall", ECHO_RESPONSE_ENCODER, ECHO_REQUEST_DECODER,
      new MethodTranscodingOptions().setPath("/bridge/query"));

  private static final TranscodingServiceMethod<Empty, Empty> EMPTY_CALL =
    TranscodingServiceMethod.server(TEST_SERVICE_NAME, "EmptyCall", EMPTY_ENCODER, EMPTY_DECODER,
      new MethodTranscodingOptions().setHttpMethod(HttpMethod.POST).setPath("/bridge/empty"));

  private HttpClient httpClient;
  private HttpServer httpServer;

  @Before
  @Override
  public void setUp(TestContext should) {
    super.setUp(should);

    TestServiceGrpc.TestServiceImplBase impl = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unaryCall(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
        responseObserver.onNext(EchoResponse.newBuilder().setPayload(request.getPayload()).build());
        responseObserver.onCompleted();
      }

      @Override
      public void emptyCall(Empty request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
      }
    };

    GrpcIoServiceBridge bridge = GrpcIoServiceBridge.bridge(impl);
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    grpcServer.callHandler(UNARY_CALL_WITH_PARAM, req -> bridge.invoker(UNARY_CALL_WITH_PARAM).invoke(req));
    grpcServer.callHandler(UNARY_CALL_WITH_BODY, req -> bridge.invoker(UNARY_CALL_WITH_BODY).invoke(req));
    grpcServer.callHandler(UNARY_CALL_WITH_QUERY, req -> bridge.invoker(UNARY_CALL_WITH_QUERY).invoke(req));
    grpcServer.callHandler(EMPTY_CALL, req -> bridge.invoker(EMPTY_CALL).invoke(req));

    httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port).setProtocolVersion(HttpVersion.HTTP_2));
    httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(port)).requestHandler(grpcServer);
    httpServer.listen().onComplete(should.asyncAssertSuccess());
  }

  @After
  @Override
  public void tearDown(TestContext should) {
    httpServer.close().onComplete(should.asyncAssertSuccess());
    httpClient.close().onComplete(should.asyncAssertSuccess());
    super.tearDown(should);
  }

  @Test
  public void testTranscodingGetWithPathParam(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/bridge/hello/" + payload).compose(req -> {
      req.headers().add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE);
      req.headers().add(HttpHeaders.ACCEPT, CONTENT_TYPE);
      return req.send().compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  @Test
  public void testTranscodingPostWithJsonBody(TestContext should) {
    String payload = "foobar";
    String jsonBody = encode(EchoRequest.newBuilder().setPayload(payload).build()).toString();
    httpClient.request(HttpMethod.POST, "/bridge/hello").compose(req -> {
      req.headers().add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE);
      req.headers().add(HttpHeaders.ACCEPT, CONTENT_TYPE);
      return req.send(jsonBody).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  @Test
  public void testTranscodingGetWithQueryParam(TestContext should) {
    String payload = "foobar";
    httpClient.request(HttpMethod.GET, "/bridge/query?payload=" + payload).compose(req -> {
      req.headers().add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE);
      req.headers().add(HttpHeaders.ACCEPT, CONTENT_TYPE);
      return req.send().compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      JsonObject body = decodeBody(response.body().result());
      assertEquals(payload, body.getString("payload"));
    })));
  }

  @Test
  public void testTranscodingEmptyCall(TestContext should) {
    httpClient.request(HttpMethod.POST, "/bridge/empty").compose(req -> {
      req.headers().add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE);
      req.headers().add(HttpHeaders.ACCEPT, CONTENT_TYPE);
      return req.send("{}").compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
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
    return new JsonObject(body.toString());
  }
}
