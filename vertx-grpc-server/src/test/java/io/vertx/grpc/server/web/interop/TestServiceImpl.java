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

package io.vertx.grpc.server.web.interop;

import com.google.protobuf.ByteString;
import grpc.testing.EmptyOuterClass;
import grpc.testing.Messages;
import grpc.testing.TestServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class TestServiceImpl extends TestServiceGrpc.TestServiceImplBase {

  private final Vertx vertx;

  TestServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void emptyCall(EmptyOuterClass.Empty request, StreamObserver<EmptyOuterClass.Empty> responseObserver) {
    responseObserver.onNext(EmptyOuterClass.Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void unaryCall(Messages.SimpleRequest request, StreamObserver<Messages.SimpleResponse> responseObserver) {
    if (request.hasResponseStatus()) {
      Messages.EchoStatus echoStatus = request.getResponseStatus();
      Status status = Status.fromCodeValue(echoStatus.getCode())
        .withDescription(echoStatus.getMessage());
      responseObserver.onError(new StatusException(status));
      return;
    }
    Messages.Payload payload = Messages.Payload.newBuilder()
      .setTypeValue(request.getResponseTypeValue())
      .setBody(ByteString.copyFrom(new byte[request.getResponseSize()]))
      .build();
    Messages.SimpleResponse response = Messages.SimpleResponse.newBuilder()
      .setPayload(payload)
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void streamingOutputCall(Messages.StreamingOutputCallRequest request, StreamObserver<Messages.StreamingOutputCallResponse> responseObserver) {
    List<Future<Void>> futures = new ArrayList<>(request.getResponseParametersCount());
    long delay = 0;
    for (Messages.ResponseParameters parameters : request.getResponseParametersList()) {
      delay += Math.max(1, MILLISECONDS.convert(parameters.getIntervalUs(), MICROSECONDS));
      Promise<Void> promise = Promise.promise();
      vertx.setTimer(delay, l -> {
        Messages.Payload payload = Messages.Payload.newBuilder()
          .setType(request.getResponseType())
          .setBody(ByteString.copyFrom(new byte[parameters.getSize()]))
          .build();
        Messages.StreamingOutputCallResponse response = Messages.StreamingOutputCallResponse.newBuilder()
          .setPayload(payload)
          .build();
        responseObserver.onNext(response);
        promise.complete();
      });
      futures.add(promise.future());
    }
    Future.join(futures).onComplete(v -> responseObserver.onCompleted());
  }
}
