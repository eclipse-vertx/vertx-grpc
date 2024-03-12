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

package io.vertx.grpc.server.web;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import io.vertx.grpcweb.GrpcWebTesting.*;
import io.vertx.grpcweb.TestServiceGrpc;

import java.util.Arrays;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

class TestServiceImpl extends TestServiceGrpc.TestServiceImplBase {

  private static final Key<String> TRAILER_ERROR_KEY = Key.of("x-error-trailer", ASCII_STRING_MARSHALLER);

  @Override
  public void emptyCall(Empty request, StreamObserver<Empty> responseObserver) {
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void unaryCall(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
    String payload = request.getPayload();
    if ("boom".equals(payload)) {
      Metadata metadata = new Metadata();
      metadata.put(TRAILER_ERROR_KEY, "boom");
      responseObserver.onError(new StatusException(Status.INTERNAL, metadata));
    } else {
      EchoResponse response = EchoResponse.newBuilder()
        .setPayload(payload)
        .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }

  @Override
  public void streamingCall(StreamingRequest request, StreamObserver<StreamingResponse> responseObserver) {
    for (int requestedSize : request.getResponseSizeList()) {
      char[] value = new char[requestedSize];
      Arrays.fill(value, 'a');
      StreamingResponse response = StreamingResponse.newBuilder().setPayload(new String(value)).build();
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }
}
