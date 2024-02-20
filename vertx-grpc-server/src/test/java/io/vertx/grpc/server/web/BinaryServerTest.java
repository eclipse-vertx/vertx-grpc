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

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * Tests for gRPC-Web server using the binary protocol.
 */
public class BinaryServerTest extends ServerTestBase {

  private static final CharSequence GRPC_WEB_PROTO = HttpHeaders.createOptimized("application/grpc-web+proto");

  @Override
  protected MultiMap requestHeaders() {
    return MultiMap.caseInsensitiveMultiMap()
      .add(CONTENT_TYPE, GRPC_WEB_PROTO)
      .add(USER_AGENT, GRPC_WEB_JAVASCRIPT_0_1)
      .add(GRPC_WEB, TRUE);
  }

  @Override
  protected CharSequence responseContentType() {
    return GRPC_WEB_PROTO;
  }

  @Override
  protected Buffer decodeBody(Buffer buffer) {
    return buffer;
  }
}
