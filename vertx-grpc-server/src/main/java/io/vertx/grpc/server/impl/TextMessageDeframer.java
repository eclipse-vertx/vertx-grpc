package io.vertx.grpc.server.impl;

import io.netty.handler.codec.base64.Base64;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.MessageSizeOverflowException;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;

class TextMessageDeframer implements GrpcMessageDeframer {

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
      BufferInternal decoded = BufferInternal.buffer(Base64.decode(((BufferInternal) buffer).getByteBuf()));
      buffer = null;
      result = GrpcMessage.message("identity", decoded.slice(5, decoded.length()));
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
