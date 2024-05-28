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
package io.vertx.grpc.common.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferInternal;
import io.vertx.grpc.common.GrpcMessage;

public class GrpcMessageImpl implements GrpcMessage {

  private final String encoding;
  private final Buffer payload;

  public GrpcMessageImpl(String encoding, Buffer payload) {
    this.encoding = encoding;
    this.payload = payload;
  }

  @Override
  public String encoding() {
    return encoding;
  }

  @Override
  public Buffer payload() {
    return payload;
  }

  public static Buffer encode(GrpcMessage message) {
    return encode(message, false);
  }

  /**
   * Encode a {@link GrpcMessage}.
   *
   * @param message the message
   * @param trailer whether this message is a gRPC-Web trailer
   * @return the encoded message
   */
  public static BufferInternal encode(GrpcMessage message, boolean trailer) {
    Buffer payload = message.payload();
    int len = payload.length();
    BufferInternal encoded = BufferInternal.buffer(5 + len);
    boolean compressed = !message.encoding().equals("identity");
    encoded.appendByte((byte) ((trailer ? 0x80 : 0x00) | (compressed ? 0x01 : 0x00)));
    encoded.appendInt(len);
    encoded.appendBuffer(payload);
    return encoded;
  }
}
