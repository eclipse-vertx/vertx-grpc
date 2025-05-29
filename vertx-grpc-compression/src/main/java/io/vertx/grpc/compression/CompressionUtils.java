/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.CodecException;

import java.util.Queue;

public final class CompressionUtils {

  private CompressionUtils() {
  }

  /**
   * Helper method to decode data using a specified decoder
   *
   * @param data the data to decode
   * @param decoder the decoder to use
   * @param errorMessage the error message to use if decoding fails
   * @return the decoded buffer
   * @throws CodecException if decoding fails
   */
  public static Buffer decode(Buffer data, ChannelHandler decoder, String errorMessage) {
    if (data.length() == 0) {
      return BufferInternal.buffer();
    }

    EmbeddedChannel channel = new EmbeddedChannel(decoder);
    channel.config().setAllocator(BufferInternal.buffer().getByteBuf().alloc());
    try {
      ChannelFuture fut = channel.writeOneInbound(((BufferInternal) data).getByteBuf());
      if (fut.isSuccess()) {
        Buffer decoded = null;
        while (true) {
          ByteBuf buf = channel.readInbound();
          if (buf == null) {
            break;
          }
          if (decoded == null) {
            decoded = BufferInternal.buffer(buf);
          } else {
            decoded.appendBuffer(BufferInternal.buffer(buf));
          }
        }
        if (decoded == null) {
          throw new CodecException(errorMessage);
        }
        return decoded;
      } else {
        throw new CodecException(fut.cause());
      }
    } finally {
      channel.close();
    }
  }

  /**
   * Helper method to encode data using a specified encoder
   *
   * @param data the data to encode
   * @param encoder the encoder to use
   * @return the encoded buffer
   */
  public static Buffer encode(Buffer data, ChannelHandler encoder) {
    if (data.length() == 0) {
      return BufferInternal.buffer();
    }

    CompositeByteBuf composite = Unpooled.compositeBuffer();
    EmbeddedChannel channel = new EmbeddedChannel(encoder);
    channel.config().setAllocator(BufferInternal.buffer().getByteBuf().alloc());
    channel.writeOutbound(((BufferInternal) data).getByteBuf());
    channel.finish();
    Queue<Object> messages = channel.outboundMessages();
    ByteBuf a;
    while ((a = (ByteBuf) messages.poll()) != null) {
      composite.addComponent(true, a);
    }
    channel.close();
    return BufferInternal.buffer(composite);
  }
}
