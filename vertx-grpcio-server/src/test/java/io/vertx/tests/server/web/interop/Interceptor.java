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

import io.grpc.*;
import io.grpc.Metadata.Key;

import java.util.Collections;
import java.util.Set;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Metadata.BINARY_BYTE_MARSHALLER;

class Interceptor implements ServerInterceptor {

  private static final Key<String> ECHO_INITIAL_KEY = Key.of("x-grpc-test-echo-initial", ASCII_STRING_MARSHALLER);
  private static final Set<Key<?>> HEADERS_KEY_SET = Collections.singleton(ECHO_INITIAL_KEY);
  private static final Key<byte[]> ECHO_TRAILING_KEY = Key.of("x-grpc-test-echo-trailing-bin", BINARY_BYTE_MARSHALLER);
  private static final Set<Key<?>> TRAILERS_KEY_SET = Collections.singleton(ECHO_TRAILING_KEY);

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
    return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {

      @Override
      public void sendHeaders(Metadata headers) {
        headers.merge(metadata, HEADERS_KEY_SET);
        super.sendHeaders(headers);
      }

      @Override
      public void close(Status status, Metadata trailers) {
        trailers.merge(metadata, TRAILERS_KEY_SET);
        super.close(status, trailers);
      }
    }, metadata);
  }
}
