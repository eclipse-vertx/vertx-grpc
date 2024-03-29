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
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferInternal;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMessageImpl;
import io.vertx.grpc.common.impl.Utils;
import io.vertx.grpc.server.GrpcServerResponse;

import java.util.Map;
import java.util.Objects;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.grpc.common.GrpcMediaType.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerResponseImpl<Req, Resp> implements GrpcServerResponse<Req, Resp> {

  private final GrpcServerRequestImpl<Req, Resp> request;
  private final HttpServerResponse httpResponse;
  private final GrpcMessageEncoder<Resp> encoder;
  private final CharSequence contentType;
  private String encoding;
  private GrpcStatus status = GrpcStatus.OK;
  private String statusMessage;
  private boolean headersSent;
  private boolean trailersSent;
  private boolean cancelled;
  private MultiMap headers, trailers;

  public GrpcServerResponseImpl(GrpcServerRequestImpl<Req, Resp> request, HttpServerResponse httpResponse, GrpcMessageEncoder<Resp> encoder) {
    this.request = request;
    this.httpResponse = httpResponse;
    this.encoder = encoder;
    if (request.httpRequest.version() != HttpVersion.HTTP_2) {
      String requestMediaType = request.headers().get(CONTENT_TYPE);
      if (isGrpcWebText(requestMediaType)) {
        contentType = GRPC_WEB_TEXT_PROTO;
      } else {
        contentType = GRPC_WEB_PROTO;
      }
    } else {
      contentType = GRPC_PROTO;
    }
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

  public GrpcServerResponse<Req, Resp> encoding(String encoding) {
    this.encoding = encoding;
    return this;
  }

  @Override
  public MultiMap headers() {
    if (headersSent) {
      throw new IllegalStateException("Headers already sent");
    }
    if (headers == null) {
      headers = MultiMap.caseInsensitiveMultiMap();
    }
    return headers;
  }

  @Override
  public MultiMap trailers() {
    if (trailersSent) {
      throw new IllegalStateException("Trailers already sent");
    }
    if (trailers == null) {
      trailers = MultiMap.caseInsensitiveMultiMap();
    }
    return trailers;
  }

  @Override
  public GrpcServerResponseImpl<Req, Resp> exceptionHandler(Handler<Throwable> handler) {
    httpResponse.exceptionHandler(handler);
    return this;
  }

  @Override
  public Future<Void> write(Resp message) {
    return writeMessage(encoder.encode(message));
  }

  @Override
  public Future<Void> end(Resp message) {
    return endMessage(encoder.encode(message));
  }

  @Override
  public Future<Void> writeMessage(GrpcMessage data) {
    return writeMessage(data, false);
  }

  @Override
  public Future<Void> endMessage(GrpcMessage message) {
    return writeMessage(message, true);
  }

  public Future<Void> end() {
    return writeMessage(null, true);
  }

  @Override
  public GrpcServerResponse<Req, Resp> setWriteQueueMaxSize(int maxSize) {
    httpResponse.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return httpResponse.writeQueueFull();
  }

  @Override
  public GrpcServerResponse<Req, Resp> drainHandler(Handler<Void> handler) {
    httpResponse.drainHandler(handler);
    return this;
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
    if (!requestEnded || !trailersSent) {
      httpResponse.reset(GrpcError.CANCELLED.http2ResetCode);
    }
  }

  private Future<Void> writeMessage(GrpcMessage message, boolean end) {

    if (cancelled) {
      throw new IllegalStateException("The stream has been cancelled");
    }
    if (trailersSent) {
      throw new IllegalStateException("The stream has been closed");
    }

    if (message == null && !end) {
      throw new IllegalStateException();
    }

    if (encoding != null && message != null && !encoding.equals(message.encoding())) {
      switch (encoding) {
        case "gzip":
          message = GrpcMessageEncoder.GZIP.encode(message.payload());
          break;
        case "identity":
          if (!message.encoding().equals("identity")) {
            if (!message.encoding().equals("gzip")) {
              return Future.failedFuture("Encoding " + message.encoding() + " is not supported");
            }
            Buffer decoded;
            try {
              decoded = GrpcMessageDecoder.GZIP.decode(message);
            } catch (CodecException e) {
              return Future.failedFuture(e);
            }
            message = GrpcMessage.message("identity", decoded);
          }
          break;
      }
    }

    boolean trailersOnly = status != GrpcStatus.OK && !headersSent && end;

    MultiMap responseHeaders = httpResponse.headers();
    if (!headersSent) {
      if (isGrpcWeb() && !trailersOnly) {
        httpResponse.setChunked(true);
      }
      headersSent = true;
      if (headers != null && !headers.isEmpty()) {
        for (Map.Entry<String, String> header : headers) {
          responseHeaders.add(header.getKey(), header.getValue());
        }
      }
      responseHeaders.set("content-type", contentType);
      if (!isGrpcWeb()) {
        responseHeaders.set("grpc-encoding", encoding);
        responseHeaders.set("grpc-accept-encoding", "gzip");
      }
    }

    if (end) {
      if (!trailersSent) {
        trailersSent = true;
      }
      MultiMap responseTrailers;
      if (trailersOnly) {
        responseTrailers = httpResponse.headers();
      } else if (!isGrpcWeb()) {
        responseTrailers = httpResponse.trailers();
      } else {
        responseTrailers = HttpHeaders.headers();
      }

      if (trailers != null && !trailers.isEmpty()) {
        for (Map.Entry<String, String> trailer : trailers) {
          responseTrailers.add(trailer.getKey(), trailer.getValue());
        }
      }
      if (!responseHeaders.contains("grpc-status")) {
        responseTrailers.set("grpc-status", status.toString());
      }
      if (status != GrpcStatus.OK) {
        String msg = statusMessage;
        if (msg != null && !responseHeaders.contains("grpc-status-message")) {
          responseTrailers.set("grpc-message", Utils.utf8PercentEncode(msg));
        }
      } else {
        responseTrailers.remove("grpc-message");
      }
      if (message != null) {
        httpResponse.write(encodeMessage(message, false));
      }
      if (isGrpcWeb() && !trailersOnly) {
        Buffer buffer = Buffer.buffer();
        for (Map.Entry<String, String> trailer : responseTrailers) {
          buffer.appendString(trailer.getKey())
            .appendString(":")
            .appendString(trailer.getValue())
            .appendString("\r\n");
        }
        httpResponse.write(encodeMessage(new GrpcMessageImpl("identity", buffer), true));
      }
      return httpResponse.end();
    } else {
      return httpResponse.write(encodeMessage(message, false));
    }
  }

  private Buffer encodeMessage(GrpcMessage message, boolean trailer) {
    BufferInternal buffer = GrpcMessageImpl.encode(message, trailer);
    if (GRPC_WEB_TEXT_PROTO.equals(contentType)) {
      return BufferInternal.buffer(Base64.encode(buffer.getByteBuf(), false));
    }
    return buffer;
  }

  private boolean isGrpcWeb() {
    return !GRPC_PROTO.equals(contentType);
  }
}
