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

import com.google.protobuf.Message;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;

import java.util.Base64;

import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.junit.Assert.assertEquals;

/**
 * Tests for gRPC-Web server using the text (base64) protocol.
 */
public class TextServerTest extends ServerTestBase {

  private static final CharSequence GRPC_WEB_TEXT = HttpHeaders.createOptimized("application/grpc-web-text");
  private static final CharSequence GRPC_WEB_TEXT_PROTO = HttpHeaders.createOptimized(GRPC_WEB_TEXT + "+proto");
  private static final Base64.Encoder ENCODER = Base64.getEncoder();
  private static final Base64.Decoder DECODER = Base64.getDecoder();

  @Override
  protected MultiMap requestHeaders() {
    return MultiMap.caseInsensitiveMultiMap()
      .add(ACCEPT, GRPC_WEB_TEXT)
      .add(CONTENT_TYPE, GRPC_WEB_TEXT)
      .add(USER_AGENT, GRPC_WEB_JAVASCRIPT_0_1)
      .add(GRPC_WEB, TRUE);
  }

  @Override
  protected CharSequence responseContentType() {
    return GRPC_WEB_TEXT_PROTO;
  }

  @Override
  protected Buffer encode(Message message) {
    Buffer buffer = super.encode(message);
    // The whole message must be encoded at once when sending
    return Buffer.buffer(ENCODER.encode(buffer.getBytes()));
  }

  @Override
  protected Buffer decodeBody(Buffer buffer) {
    // The server sends base64 encoded chunks of arbitrary size
    // All we know is that a 4-bytes block is always a valid base64 payload
    assertEquals(0, buffer.length() % 4);
    Buffer res = Buffer.buffer();
    for (int i = 0; i < buffer.length(); i += 4) {
      byte[] block = buffer.getBytes(i, i + 4);
      res.appendBytes(DECODER.decode(block));
    }
    return res;
  }
}
