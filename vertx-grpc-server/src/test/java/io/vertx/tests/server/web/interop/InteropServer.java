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

package io.vertx.tests.server.web.interop;

import com.google.protobuf.ByteString;
import grpc.testing.EmptyOuterClass;
import grpc.testing.Messages;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.tests.server.web.ServerTestBase;

import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A gRPC-Web server for grpc-web interop tests.
 */
public class InteropServer extends AbstractVerticle {

  public static GrpcMessageDecoder<EmptyOuterClass.Empty> EMPTY_DECODER = GrpcMessageDecoder.decoder(EmptyOuterClass.Empty.parser());
  public static GrpcMessageEncoder<EmptyOuterClass.Empty> EMPTY_ENCODER = GrpcMessageEncoder.encoder();
  public static GrpcMessageDecoder<Messages.SimpleRequest> ECHO_REQUEST_DECODER = GrpcMessageDecoder.decoder(Messages.SimpleRequest.parser());
  public static GrpcMessageEncoder<Messages.SimpleResponse> ECHO_RESPONSE_ENCODER = GrpcMessageEncoder.encoder();
  public static GrpcMessageDecoder<Messages.StreamingOutputCallRequest> STREAMING_REQUEST_DECODER = GrpcMessageDecoder.decoder(Messages.StreamingOutputCallRequest.parser());
  public static GrpcMessageEncoder<Messages.StreamingOutputCallResponse> STREAMING_RESPONSE_ENCODER = GrpcMessageEncoder.encoder();

  public static final ServiceName TEST_SERVICE_NAME = ServiceName.create("grpc.testing.TestService");
  public static final ServiceMethod<EmptyOuterClass.Empty, EmptyOuterClass.Empty> EMPTY_CALL = ServiceMethod.server(TEST_SERVICE_NAME, "EmptyCall", EMPTY_ENCODER, EMPTY_DECODER);
  public static final ServiceMethod<Messages.SimpleRequest, Messages.SimpleResponse> UNARY_CALL = ServiceMethod.server(TEST_SERVICE_NAME, "UnaryCall", ECHO_RESPONSE_ENCODER, ECHO_REQUEST_DECODER);
  public static final ServiceMethod<Messages.StreamingOutputCallRequest, Messages.StreamingOutputCallResponse> STREAMING_OUTPUT_CALL = ServiceMethod.server(TEST_SERVICE_NAME, "StreamingOutputCall", STREAMING_RESPONSE_ENCODER, STREAMING_REQUEST_DECODER);

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new InteropServer())
      .onFailure(Throwable::printStackTrace)
      .onSuccess(v -> System.out.println("Deployed InteropServer"));
  }

  @Override
  public void start(Promise<Void> startPromise) {
    GrpcServer grpcServer = GrpcServer.server(vertx, new GrpcServerOptions().setGrpcWebEnabled(true));

    grpcServer.callHandler(EMPTY_CALL, request -> {
      ServerTestBase.copyMetadata(request.headers(), request.response().headers(), "x-grpc-test-echo-initial");
      ServerTestBase.copyMetadata(request.headers(), request.response().trailers(), "x-grpc-test-echo-trailing-bin");
      request.response().end(EmptyOuterClass.Empty.newBuilder().build());
    });

    grpcServer.callHandler(UNARY_CALL, request -> {
      ServerTestBase.copyMetadata(request.headers(), request.response().headers(), "x-grpc-test-echo-initial");
      ServerTestBase.copyMetadata(request.headers(), request.response().trailers(), "x-grpc-test-echo-trailing-bin");
      request.handler(requestMsg -> {
        if (requestMsg.hasResponseStatus()) {
          Messages.EchoStatus echoStatus = requestMsg.getResponseStatus();
          request.response()
            .status(GrpcStatus.valueOf(echoStatus.getCode()))
            .statusMessage(echoStatus.getMessage())
            .end();
          return;
        }
        Messages.Payload payload = Messages.Payload.newBuilder()
          .setTypeValue(requestMsg.getResponseTypeValue())
          .setBody(ByteString.copyFrom(new byte[requestMsg.getResponseSize()]))
          .build();
        Messages.SimpleResponse response = Messages.SimpleResponse.newBuilder()
          .setPayload(payload)
          .build();
        request.response().end(response);
      });
    });
    // Fallback
    grpcServer.callHandler(request -> {
      request
        .response()
        .status(GrpcStatus.UNIMPLEMENTED)
        .statusMessage(String.format("Method %s is unimplemented",
          request.fullMethodName()))
        .end();
    });
    grpcServer.callHandler(STREAMING_OUTPUT_CALL, request -> {
      ServerTestBase.copyMetadata(request.headers(), request.response().headers(), "x-grpc-test-echo-initial");
      ServerTestBase.copyMetadata(request.headers(), request.response().trailers(), "x-grpc-test-echo-trailing-bin");
      request.handler(requestMsg -> {
        List<Future<Void>> futures = new ArrayList<>(requestMsg.getResponseParametersCount());
        long delay = 0;
        for (Messages.ResponseParameters parameters : requestMsg.getResponseParametersList()) {
          delay += Math.max(1, MILLISECONDS.convert(parameters.getIntervalUs(), MICROSECONDS));
          Promise<Void> promise = Promise.promise();
          vertx.setTimer(delay, l -> {
            Messages.Payload payload = Messages.Payload.newBuilder()
              .setType(requestMsg.getResponseType())
              .setBody(ByteString.copyFrom(new byte[parameters.getSize()]))
              .build();
            Messages.StreamingOutputCallResponse response = Messages.StreamingOutputCallResponse.newBuilder()
              .setPayload(payload)
              .build();
            request.response().write(response);
            promise.complete();
          });
          futures.add(promise.future());
        }
        Future.join(futures).onComplete(v -> request.response().end());
      });
    });

    vertx.createHttpServer()
      .requestHandler(grpcServer)
      .listen(8080)
      .<Void>mapEmpty()
      .onComplete(startPromise);
  }
}
