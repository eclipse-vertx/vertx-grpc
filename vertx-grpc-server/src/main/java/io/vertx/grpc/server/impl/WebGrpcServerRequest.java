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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcProtocol;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class WebGrpcServerRequest<Req, Resp> extends GrpcServerRequestImpl<Req, Resp> {

  private static final BufferInternal EMPTY_BUFFER = BufferInternal.buffer(Unpooled.EMPTY_BUFFER);

  private BufferInternal grpcWebTextBuffer;

  public WebGrpcServerRequest(ContextInternal context, boolean scheduleDeadline, GrpcProtocol protocol, WireFormat format, long maxMessageSize, HttpServerRequest httpRequest, GrpcMessageDecoder<Req> messageDecoder, GrpcMethodCall methodCall) {
    super(context, scheduleDeadline, protocol, format, maxMessageSize, httpRequest, messageDecoder, methodCall);

    if (httpRequest.version() != HttpVersion.HTTP_2 && GrpcMediaType.isGrpcWebText(httpRequest.getHeader(CONTENT_TYPE))) {
      grpcWebTextBuffer = EMPTY_BUFFER;
    } else {
      grpcWebTextBuffer = null;
    }
  }

  protected final boolean notGrpcWebText() {
    return grpcWebTextBuffer == null;
 }

  @Override
  public void handle(Buffer chunk) {
    if (notGrpcWebText()) {
      super.handle(chunk);
    } else {
      if (grpcWebTextBuffer == EMPTY_BUFFER) {
        ByteBuf bbuf = ((BufferInternal) chunk).getByteBuf();
        if ((chunk.length() & 0b11) == 0) {
          // Content length is divisible by four, so we decode it immediately
          super.handle(BufferInternal.buffer(Base64.decode(bbuf)));
        } else {
          grpcWebTextBuffer = BufferInternal.buffer(bbuf.copy());
        }
        return;
      }
      bufferAndDecode(chunk);
    }
  }

  private void bufferAndDecode(Buffer chunk) {
    grpcWebTextBuffer.appendBuffer(chunk);
    int len = grpcWebTextBuffer.length();
    // Decode base64 content as soon as we have more bytes than a multiple of four.
    // We could instead wait for the buffer length to be a multiple of four,
    // But then in the worst case we may have to buffer the whole request.
    int maxDecodable = len & ~0b11;
    if (maxDecodable == len) {
        BufferInternal decoded = BufferInternal.buffer(Base64.decode(grpcWebTextBuffer.getByteBuf()));
        grpcWebTextBuffer = EMPTY_BUFFER;
        super.handle(decoded);
    } else if (maxDecodable > 0) {
        ByteBuf bbuf = grpcWebTextBuffer.getByteBuf();
        BufferInternal decoded = BufferInternal.buffer(Base64.decode(bbuf, 0, maxDecodable));
        grpcWebTextBuffer = BufferInternal.buffer(bbuf.copy(maxDecodable, len - maxDecodable));
        super.handle(decoded);
    }
  }
}
