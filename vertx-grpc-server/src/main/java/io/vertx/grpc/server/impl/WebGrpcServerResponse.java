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
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Map;

import static io.vertx.grpc.server.GrpcProtocol.WEB_TEXT;

public class WebGrpcServerResponse<Req, Resp> extends GrpcServerResponseImpl<Req,Resp> {

  private final GrpcProtocol protocol;
  private final HttpServerResponse httpResponse;

  public WebGrpcServerResponse(ContextInternal context, GrpcServerRequestImpl<Req, Resp> request, GrpcProtocol protocol, HttpServerResponse httpResponse, GrpcMessageEncoder<Resp> encoder) {
    super(context, request, protocol, httpResponse, encoder);

    this.protocol = protocol;
    this.httpResponse = httpResponse;
  }

  @Override
  protected Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    message = super.encodeMessage(message, compressed, trailer);
    if (protocol == WEB_TEXT) {
      message = BufferInternal.buffer(Base64.encode(((BufferInternal)message).getByteBuf(), false));
    }
    return message;
  }

  @Override
  protected void setHeaders(String contentType, MultiMap grpcHeaders, boolean isEnd) {
    super.setHeaders(contentType, grpcHeaders, isEnd);
    // We need chunked to get response trailers to be sent
    httpResponse.setChunked(!isTrailersOnly());
  }

  @Override
  protected void setTrailers(MultiMap grpcTrailers) {
    if (isTrailersOnly()) {
      encodeGrpcTrailers(grpcTrailers, httpResponse.headers());
    } else {
      MultiMap httpTrailers = HttpHeaders.headers();
      encodeGrpcTrailers(grpcTrailers, httpTrailers);
      Buffer buffer = Buffer.buffer();
      for (Map.Entry<String, String> trailer : httpTrailers) {
        buffer.appendString(trailer.getKey())
          .appendByte((byte) ':')
          .appendString(trailer.getValue())
          .appendString("\r\n");
      }
      httpResponse.write(encodeMessage(buffer, false, true));
    }
  }
}
