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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferInternal;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;
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
    ByteBuf bbuf = ((BufferInternal) message.payload()).getByteBuf();
    int len = bbuf.readableBytes();
    boolean compressed = !message.encoding().equals("identity");
    // it is worthy to just copy here, 'cause composite (heap) buffers are slower to be sent to the wire or copied
    if (len <= 128) {
      int totalBytes = 5 + len;
      // let's use vertx buffer here because it doesn't have any atomic release, if unpooled
      ByteBuf fullMsg = VertxByteBufAllocator.DEFAULT.heapBuffer(totalBytes, totalBytes);
      fullMsg.setByte(0, compressed ? 1 : 0); // Compression flag
      fullMsg.setInt(1, len);                 // Length
      fullMsg.setBytes(5, bbuf, bbuf.readerIndex(), len);
      fullMsg.writerIndex(totalBytes);
      try {
        return BufferInternal.buffer(fullMsg);
      } finally {
        bbuf.release();
      }
    }
    // slow-path
    ByteBuf prefix = Unpooled.buffer(5, 5);
    prefix.writeByte(compressed ? 1 : 0);      // Compression flag
    prefix.writeInt(len);                      // Length
    CompositeByteBuf composite = Unpooled.compositeBuffer(2);
    composite.addComponent(true, 0, prefix);
    composite.addComponent(true, 1, bbuf);
    return BufferInternal.buffer(composite);
  }
}
