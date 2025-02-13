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

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcStatus;
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
public abstract class GrpcServerResponseImpl<Req, Resp> extends GrpcWriteStreamBase<GrpcServerResponseImpl<Req, Resp>, Resp> implements GrpcServerResponse<Req, Resp> {

  private final GrpcServerRequestImpl<Req, Resp> request;
  private final HttpServerResponse httpResponse;
  private final GrpcProtocol protocol;
  private GrpcStatus status = GrpcStatus.OK;
  private String statusMessage;
  private boolean trailersOnly;
  private boolean cancelled;

  public GrpcServerResponseImpl(ContextInternal context,
                                GrpcServerRequestImpl<Req, Resp> request,
                                GrpcProtocol protocol,
                                HttpServerResponse httpResponse,
                                GrpcMessageEncoder<Resp> encoder) {
    super(context, protocol.mediaType(), httpResponse, encoder);
    this.request = request;
    this.httpResponse = httpResponse;
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
      sendCancel();
    }
  }

  public boolean isTrailersOnly() {
    return trailersOnly;
  }

  public GrpcStatus status() {
    return status;
  }

  protected void sendCancel() {
    httpResponse
      .reset(GrpcError.CANCELLED.http2ResetCode)
      .onSuccess(v -> handleError(GrpcError.CANCELLED));
  }

  protected void setHeaders(String contentType, MultiMap grpcHeaders, boolean isEnd) {
    trailersOnly = status != GrpcStatus.OK && isEnd;
    MultiMap httpHeaders = httpResponse.headers();
    httpHeaders.set("content-type", contentType);
    encodeGrpcHeaders(grpcHeaders, httpHeaders);
  }

  protected void encodeGrpcHeaders(MultiMap grpcHeaders, MultiMap httpHeaders) {
    if (grpcHeaders != null && !grpcHeaders.isEmpty()) {
      for (Map.Entry<String, String> header : grpcHeaders) {
        httpHeaders.add(header.getKey(), header.getValue());
      }
    }
  }

  protected void setTrailers(MultiMap grpcTrailers) {
  }

  protected void encodeGrpcTrailers(MultiMap grpcTrailers, MultiMap httpTrailers) {
    MultiMap httpHeaders = httpResponse.headers();
    if (grpcTrailers != null && !grpcTrailers.isEmpty()) {
      for (Map.Entry<String, String> trailer : grpcTrailers) {
        httpTrailers.add(trailer.getKey(), trailer.getValue());
      }
    }
    if (!httpHeaders.contains("grpc-status")) {
      httpTrailers.set("grpc-status", status.toString());
    }
    if (status != GrpcStatus.OK) {
      String msg = statusMessage;
      if (msg != null && !httpHeaders.contains("grpc-message")) {
        httpTrailers.set("grpc-message", Utils.utf8PercentEncode(msg));
      }
    } else {
      httpTrailers.remove("grpc-message");
    }
  }

  @Override
  protected Future<Void> sendMessage(Buffer message, boolean compressed) {
    return httpResponse.write(encodeMessage(message, compressed, false));
  }

  protected Future<Void> sendEnd() {
    request.cancelTimeout();
    return httpResponse.end();
  }

  protected Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    return GrpcMessageImpl.encode(message, compressed, trailer);
  }
}
