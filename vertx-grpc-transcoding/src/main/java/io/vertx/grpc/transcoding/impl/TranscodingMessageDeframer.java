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
package io.vertx.grpc.transcoding.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.MessageSizeOverflowException;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;

/**
 *
 */
public class TranscodingMessageDeframer implements GrpcMessageDeframer {

  private long maxMessageSize;
  private boolean processed;
  private Buffer buffer;
  private Object result;

  @Override
  public void maxMessageSize(long maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  @Override
  public void update(Buffer chunk) {
    if (processed) {
      return;
    }
    if (buffer == null) {
      buffer = chunk;
    } else {
      try {
        buffer.appendBuffer(chunk);
      } catch (IndexOutOfBoundsException e) {
        // Work around because we cannot happend to slices
        //          java.lang.IndexOutOfBoundsException: writerIndex(270) + minWritableBytes(120) exceeds maxCapacity(270): UnpooledSlicedByteBuf(ridx: 0, widx: 270, cap: 270/270, unwrapped: VertxUnsafeHeapByteBuf(ridx: 0, widx: 0, cap: 270))
        //          at io.netty.buffer@4.2.0.RC3/io.netty.buffer.AbstractByteBuf.ensureWritable0(AbstractByteBuf.java:294)
        //          at io.netty.buffer@4.2.0.RC3/io.netty.buffer.AbstractByteBuf.ensureWritable(AbstractByteBuf.java:280)
        //          at io.netty.buffer@4.2.0.RC3/io.netty.buffer.AbstractByteBuf.writeBytes(AbstractByteBuf.java:1103)
        //          at io.vertx.core@5.0.0-SNAPSHOT/io.vertx.core.buffer.impl.BufferImpl.appendBuffer(BufferImpl.java:256)
        //          at io.vertx.core@5.0.0-SNAPSHOT/io.vertx.core.buffer.impl.BufferImpl.appendBuffer(BufferImpl.java:41)
        buffer = buffer.copy();
        buffer.appendBuffer(chunk);
      }
    }
    if (result == null && buffer.length() > maxMessageSize) {
      result = new MessageSizeOverflowException(buffer.length());
      buffer = null;
      processed = true;
    }
  }

  @Override
  public void end() {
    if (!processed) {
      result = GrpcMessage.message("identity", WireFormat.JSON, buffer == null ? Buffer.buffer() : buffer);
      buffer = null;
    }
  }

  @Override
  public Object next() {
    if (result != null) {
      Object ret = result;
      result = null;
      return ret;
    } else {
      return null;
    }
  }
}
