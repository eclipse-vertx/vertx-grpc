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

import io.netty.handler.codec.base64.Base64;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMessageImpl;
import io.vertx.grpc.common.impl.GrpcWriteStreamBase;
import io.vertx.grpc.common.impl.Utils;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServerResponse;

import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerResponseImpl<Req, Resp> extends GrpcWriteStreamBase<GrpcServerResponseImpl<Req, Resp>, Resp> implements GrpcServerResponse<Req, Resp> {

  private final GrpcServerRequestImpl<Req, Resp> request;
  private final HttpServerResponse httpResponse;
  private final String transcodingResponseBody;
  private final GrpcProtocol protocol;
  private GrpcStatus status = GrpcStatus.OK;
  private String statusMessage;
  private MultiMap httpResponseTrailers;
  private boolean trailersOnly;
  private boolean cancelled;

  public GrpcServerResponseImpl(ContextInternal context,
    GrpcServerRequestImpl<Req, Resp> request,
    GrpcProtocol protocol,
    HttpServerResponse httpResponse,
    String transcodingResponseBody,
    GrpcMessageEncoder<Resp> encoder) {
    super(context, protocol.mediaType(), httpResponse, encoder);
    this.request = request;
    this.httpResponse = httpResponse;
    this.transcodingResponseBody = transcodingResponseBody;
    this.protocol = protocol;
  }

  public GrpcServerResponse<Req, Resp> status(GrpcStatus status) {
    Objects.requireNonNull(status);
    this.status = status;
    return this;
  }

  @Override
  public GrpcServerResponse<Req, Resp> statusMessage(String msg) {
    this.statusMessage = msg;
    return this;
  }

  public void handleTimeout() {
    if (!isCancelled()) {
      if (!isTrailersSent()) {
        status(GrpcStatus.DEADLINE_EXCEEDED);
        end();
      } else {
        cancel();
      }
    }
  }

  @Override
  public void cancel() {
    if (cancelled) {
      return;
    }
    cancelled = true;
    Future<Void> fut = request.end();
    boolean requestEnded;
    if (fut.failed()) {
      return;
    } else {
      requestEnded = fut.succeeded();
    }
    if (!requestEnded || !isTrailersSent()) {
      httpResponse
        .reset(GrpcError.CANCELLED.http2ResetCode)
        .onSuccess(v -> handleError(GrpcError.CANCELLED));
    }
  }

  protected void sendHeaders(String contentType, MultiMap headers, boolean end) {
    MultiMap responseHeaders = httpResponse.headers();
    trailersOnly = status != GrpcStatus.OK && end;
    httpResponse.setChunked(protocol.isWeb() && !trailersOnly);
    if (headers != null && !headers.isEmpty()) {
      for (Map.Entry<String, String> header : headers) {
        responseHeaders.add(header.getKey(), header.getValue());
      }
    }
    responseHeaders.set("content-type", contentType);
    if (!protocol.isWeb()) {
      responseHeaders.set("grpc-encoding", encoding);
      responseHeaders.set("grpc-accept-encoding", "gzip");
    }
  }

  protected void sendTrailers(MultiMap trailers) {
    if (trailersOnly) {
      httpResponseTrailers = httpResponse.headers();
    } else if (!protocol.isWeb()) {
      httpResponseTrailers = httpResponse.trailers();
    } else {
      httpResponseTrailers = HttpHeaders.headers();
    }

    MultiMap responseHeaders = httpResponse.headers();
    if (trailers != null && !trailers.isEmpty()) {
      for (Map.Entry<String, String> trailer : trailers) {
        httpResponseTrailers.add(trailer.getKey(), trailer.getValue());
      }
    }
    if (!responseHeaders.contains("grpc-status")) {
      httpResponseTrailers.set("grpc-status", status.toString());
    }
    if (status != GrpcStatus.OK) {
      String msg = statusMessage;
      if (msg != null && !responseHeaders.contains("grpc-status-message")) {
        httpResponseTrailers.set("grpc-message", Utils.utf8PercentEncode(msg));
      }
    } else {
      httpResponseTrailers.remove("grpc-message");
    }
    if (protocol.isWeb() && !trailersOnly) {
      Buffer buffer = Buffer.buffer();
      for (Map.Entry<String, String> trailer : httpResponseTrailers) {
        buffer.appendString(trailer.getKey())
          .appendByte((byte) ':')
          .appendString(trailer.getValue())
          .appendString("\r\n");
      }
      httpResponse.write(encodeMessage(buffer, false, true));
    }
  }

  @Override
  protected Future<Void> sendMessage(Buffer message, boolean compressed) {
    if (request.isTranscodable()) {
      return sendTranscodedMessage(message, compressed);
    }

    return httpResponse.write(encodeMessage(message, compressed, false));
  }

  private Future<Void> sendTranscodedMessage(Buffer message, boolean compressed) {
    try {
      if (transcodingResponseBody != null && !transcodingResponseBody.isEmpty()) {
        Object value = new JsonObject(message.toString());
        if (!transcodingResponseBody.equals("*")) {
          // Extract the value at the specified path
          String[] path = transcodingResponseBody.split("\\.");
          for (String field : path) {
            if (value instanceof JsonObject) {
              value = ((JsonObject) value).getValue(field);
            } else {
              // Handle the case where the path is invalid
              throw new IllegalStateException("Invalid transcodingResponseBody path: " + transcodingResponseBody);
            }
          }
        }
        message = Buffer.buffer(value.toString());
      }

      BufferInternal transcoded = (BufferInternal) message;
      httpResponse.putHeader("content-length", Integer.toString(message.length()));
      httpResponse.putHeader("content-type", GrpcProtocol.HTTP_1.mediaType());
      return httpResponse.write(transcoded);
    } catch (DecodeException e) {
      return Future.failedFuture(e);
    }
  }

  protected Future<Void> sendEnd() {
    request.cancelTimeout();
    return httpResponse.end();
  }

  private Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    BufferInternal buffer = GrpcMessageImpl.encode(message, compressed, trailer);
    if (protocol == GrpcProtocol.WEB_TEXT) {
      return BufferInternal.buffer(Base64.encode(buffer.getByteBuf(), false));
    }

    return buffer;
  }
}
