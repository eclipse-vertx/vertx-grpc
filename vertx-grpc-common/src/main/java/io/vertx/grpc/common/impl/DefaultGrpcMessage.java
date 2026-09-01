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
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.GrpcMessage;

import java.util.Objects;

public class DefaultGrpcMessage implements GrpcMessage {

  private final String encoding;
  private final WireFormat format;
  private final Buffer payload;

  public DefaultGrpcMessage(String encoding, WireFormat format, Buffer payload) {
    this.encoding = Objects.requireNonNull(encoding);
    this.format = Objects.requireNonNull(format);
    this.payload = Objects.requireNonNull(payload);
  }

  @Override
  public String encoding() {
    return encoding;
  }

  @Override
  public WireFormat format() {
    return format;
  }

  @Override
  public Buffer payload() {
    return payload;
  }

  /**
   * Encode a {@link GrpcMessage}.
   *
   * @param message the message
   * @return the encoded message
   */
  public static BufferInternal encode(GrpcMessage message) {
    return encode(message.payload(), !message.encoding().equals("identity"));
  }

  /**
   * Encode a gRPC message;
   *
   * @param payload    the message
   * @param compressed wether the message is compressed
   * @return the encoded message
   */
  public static BufferInternal encode(Buffer payload, boolean compressed) {
    int len = payload.length();
    BufferInternal encoded = BufferInternal.buffer(5 + len);
    encoded.appendByte((byte) (compressed ? 0x01 : 0x00));
    encoded.appendInt(len);
    encoded.appendBuffer(payload);
    return encoded;
  }
}
