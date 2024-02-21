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
package io.vertx.grpc.server.impl;

import io.netty.buffer.Unpooled;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferInternal;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.common.impl.GrpcReadStreamBase;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;

import java.util.Base64;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerRequestImpl<Req, Resp> extends GrpcReadStreamBase<GrpcServerRequestImpl<Req, Resp>, Req> implements GrpcServerRequest<Req, Resp> {

  private static final Base64.Decoder DECODER = Base64.getDecoder();
  private static final Buffer EMPTY_BUFFER = BufferInternal.buffer(Unpooled.EMPTY_BUFFER);

  final HttpServerRequest httpRequest;
  final GrpcServerResponse<Req, Resp> response;
  private final GrpcMethodCall methodCall;
  private Buffer grpcWebTextBuffer;

  public GrpcServerRequestImpl(HttpServerRequest httpRequest, GrpcMessageDecoder<Req> messageDecoder, GrpcMessageEncoder<Resp> messageEncoder, GrpcMethodCall methodCall) {
    super(((HttpServerRequestInternal) httpRequest).context(), httpRequest, httpRequest.headers().get("grpc-encoding"), messageDecoder);
    this.httpRequest = httpRequest;
    this.response = new GrpcServerResponseImpl<>(this, httpRequest.response(), messageEncoder);
    this.methodCall = methodCall;
    if (httpRequest.version() != HttpVersion.HTTP_2 && GrpcMediaType.isGrpcWebText(httpRequest.getHeader(CONTENT_TYPE))) {
      grpcWebTextBuffer = EMPTY_BUFFER;
    } else {
      grpcWebTextBuffer = null;
    }
  }

  public String fullMethodName() {
    return methodCall.fullMethodName();
  }

  @Override
  public MultiMap headers() {
    return httpRequest.headers();
  }

  @Override
  public String encoding() {
    return httpRequest.getHeader("grpc-encoding");
  }

  @Override
  public ServiceName serviceName() {
    return methodCall.serviceName();
  }

  @Override
  public String methodName() {
    return methodCall.methodName();
  }

  @Override
  public GrpcServerRequest<Req, Resp> handler(Handler<Req> handler) {
    if (handler != null) {
      return messageHandler(msg -> {
        Req decoded;
        try {
          decoded = decodeMessage(msg);
        } catch (CodecException e) {
          response.cancel();
          return;
        }
        handler.handle(decoded);
      });
    } else {
      return messageHandler(null);
    }
  }

  public GrpcServerResponse<Req, Resp> response() {
    return response;
  }

  @Override
  public HttpConnection connection() {
    return httpRequest.connection();
  }

  @Override
  public void handle(Buffer chunk) {
    if (notGrpcWebText()) {
      super.handle(chunk);
      return;
    }
    if (grpcWebTextBuffer == EMPTY_BUFFER) {
      if ((chunk.length() & 0b11) == 0) {
        // Content length is divisible by four, so we decode it immediately
        super.handle(Buffer.buffer(DECODER.decode(chunk.getBytes())));
      } else {
        grpcWebTextBuffer = chunk.copy();
      }
      return;
    }
    bufferAndDecode(chunk);
  }

  private boolean notGrpcWebText() {
    return grpcWebTextBuffer == null;
  }

  private void bufferAndDecode(Buffer chunk) {
    grpcWebTextBuffer.appendBuffer(chunk);
    int len = grpcWebTextBuffer.length();
    // Decode base64 content as soon as we have more bytes than a multiple of four.
    // We could instead wait for the buffer length to be a multiple of four,
    // But then in the worst case we may have to buffer the whole request.
    int maxDecodable = len & ~0b11;
    if (maxDecodable == len) {
      Buffer decoded = Buffer.buffer(DECODER.decode(grpcWebTextBuffer.getBytes()));
      grpcWebTextBuffer = EMPTY_BUFFER;
      super.handle(decoded);
    } else if (maxDecodable > 0) {
      Buffer decoded = Buffer.buffer(DECODER.decode(grpcWebTextBuffer.getBytes(0, maxDecodable)));
      grpcWebTextBuffer = grpcWebTextBuffer.getBuffer(maxDecodable, len);
      super.handle(decoded);
    }
  }
}
