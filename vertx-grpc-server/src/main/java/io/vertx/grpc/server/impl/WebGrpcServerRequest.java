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
package io.vertx.grpc.server.impl;

import io.netty.handler.codec.base64.Base64;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.common.impl.Http2GrpcMessageDeframer;
import io.vertx.grpc.server.GrpcProtocol;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class WebGrpcServerRequest<Req, Resp> extends GrpcServerRequestImpl<Req, Resp> {

  static class TextMessageDeframer implements GrpcMessageDeframer {

    private boolean ended;
    private boolean processed;
    private Buffer buffer;

    @Override
    public void update(Buffer chunk) {
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
    }

    @Override
    public void end() {
      ended = true;
    }

    @Override
    public Object next() {
      if (!ended || processed) {
        return null;
      }
      processed = true;
      if (buffer == null) {
        return null;
      }
      BufferInternal decoded = BufferInternal.buffer(Base64.decode(((BufferInternal)buffer).getByteBuf()));
      GrpcMessage ret = GrpcMessage.message("identity", decoded.slice(5, decoded.length()));
      buffer = null;
      return ret;
    }
  }

  public WebGrpcServerRequest(ContextInternal context, boolean scheduleDeadline, GrpcProtocol protocol, WireFormat format, long maxMessageSize, HttpServerRequest httpRequest, GrpcMessageDecoder<Req> messageDecoder, GrpcMethodCall methodCall) {
    super(context, scheduleDeadline, protocol, format, httpRequest, httpRequest.version() != HttpVersion.HTTP_2 && GrpcMediaType.isGrpcWebText(httpRequest.getHeader(CONTENT_TYPE)) ? new TextMessageDeframer() : new Http2GrpcMessageDeframer(maxMessageSize, httpRequest.headers().get("grpc-encoding"), format), messageDecoder, methodCall);
  }
}
