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
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.impl.GrpcWriteStreamBase;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.StatusException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class GrpcServerResponseImpl<Req, Resp> extends GrpcWriteStreamBase<GrpcServerResponseImpl<Req, Resp>, Resp> implements GrpcServerResponse<Req, Resp> {

  private static final Pattern COMMA_SEPARATOR = Pattern.compile(" *, *");
  private static final Set<String> GZIP_ACCEPT_ENCODING = Collections.singleton("gzip");

  private final GrpcServerRequestImpl<Req, Resp> request;
  private final GrpcServerInvoker invoker;
  private GrpcStatus status = GrpcStatus.OK;
  private String statusMessage;
  private Set<String> acceptedEncodings;

  public GrpcServerResponseImpl(ContextInternal context,
                                GrpcServerRequestImpl<Req, Resp> request,
                                GrpcServerInvoker invoker,
                                GrpcProtocol protocol,
                                WriteStream<?> response,
                                GrpcMessageEncoder<Resp> encoder) {
    super(context, protocol.mediaType(), response, encoder);
    this.invoker = invoker;
    this.request = request;
  }

  public GrpcServerResponse<Req, Resp> status(GrpcStatus status) {
    if (isTrailersSent()) {
      throw new IllegalStateException("Trailers have already been sent");
    }
    this.status = Objects.requireNonNull(status);
    return this;
  }

  @Override
  public GrpcServerResponse<Req, Resp> statusMessage(String msg) {
    if (isTrailersSent()) {
      throw new IllegalStateException("Trailers have already been sent");
    }
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

  public void fail(Throwable failure) {
    if (failure instanceof StatusException) {
      StatusException se = (StatusException) failure;
      this.status = se.status();
      this.statusMessage = se.message();
    } else {
      this.status = mapStatus(failure);
    }
    end();
  }

  public GrpcStatus status() {
    return status;
  }

  @Override
  public Set<String> acceptedEncodings() {
    if (acceptedEncodings == null) {
      String acceptEncodingHeader = request.headers().get("grpc-accept-encoding");
      if (acceptEncodingHeader != null) {
        if (acceptEncodingHeader.equals("gzip")) {
          acceptedEncodings = GZIP_ACCEPT_ENCODING;
        } else {
          acceptedEncodings = new HashSet<>(2);
          String[] encodings = COMMA_SEPARATOR.split(acceptEncodingHeader);
          for (String encoding : encodings) {
            acceptedEncodings.add(encoding.trim());
          }
        }
      } else {
        acceptedEncodings = Collections.emptySet();
      }
    }
    return acceptedEncodings;
  }

  protected boolean sendCancel() {
    if (!isTrailersSent()) {
      status(GrpcStatus.CANCELLED);
      end();
      return true;
    } else {
      // Can this happen ?
      return false;
    }
  }

  @Override
  protected Future<Void> sendTrailers(String contentType, String encoding, MultiMap headers, MultiMap trailers) {
    invoker.writeTrailers(contentType, encoding, status, statusMessage, headers, trailers);
    return sendEnd();
  }

  protected Future<Void> sendTrailers(MultiMap grpcTrailers) {
    invoker.writeTrailers(grpcTrailers, status, statusMessage);
    return sendEnd();
  }

  private Future<Void> sendEnd() {
    handleStatus(status);
    request.cancelTimeout();
    return invoker.writeEnd();
  }

  @Override
  protected Future<Void> sendMessage(GrpcMessage message) {
    return invoker.writeMessage(message);
  }

  @Override
  protected Future<Void> sendHeaders(String contentType, String encoding, MultiMap headers) {
    return invoker.writeHeaders(contentType, headers, status, statusMessage, encoding);
  }

  private static GrpcStatus mapStatus(Throwable t) {
    if (t instanceof StatusException) {
      return ((StatusException)t).status();
    } else if (t instanceof UnsupportedOperationException) {
      return GrpcStatus.UNIMPLEMENTED;
    } else {
      return GrpcStatus.UNKNOWN;
    }
  }
}
