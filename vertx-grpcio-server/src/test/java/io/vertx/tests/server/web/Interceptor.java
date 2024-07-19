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

import io.grpc.*;
import io.grpc.Metadata.Key;

import java.util.Set;
import java.util.stream.Stream;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Metadata.BINARY_BYTE_MARSHALLER;
import static java.util.stream.Collectors.toSet;

class Interceptor implements ServerInterceptor {

  private static final Key<String> HEADER_TEXT_KEY = Key.of("x-header-text-key", ASCII_STRING_MARSHALLER);
  private static final Key<byte[]> HEADER_BIN_KEY = Key.of("x-header-bin-key-bin", BINARY_BYTE_MARSHALLER);
  private static final Set<Key<?>> HEADERS_KEY_SET = Stream.of(HEADER_TEXT_KEY, HEADER_BIN_KEY).collect(toSet());
  private static final Key<String> TRAILER_TEXT_KEY = Key.of("x-trailer-text-key", ASCII_STRING_MARSHALLER);
  private static final Key<byte[]> TRAILER_BIN_KEY = Key.of("x-trailer-bin-key-bin", BINARY_BYTE_MARSHALLER);
  private static final Set<Key<?>> TRAILERS_KEY_SET = Stream.of(TRAILER_TEXT_KEY, TRAILER_BIN_KEY).collect(toSet());

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
