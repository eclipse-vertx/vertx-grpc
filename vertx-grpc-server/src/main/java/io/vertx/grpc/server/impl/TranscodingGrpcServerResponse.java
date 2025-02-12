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

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.transcoding.MessageWeaver;

public class TranscodingGrpcServerResponse<Req, Resp> extends GrpcServerResponseImpl<Req,Resp> {

  private final TranscodingGrpcServerRequest<Req, Resp> request;
  private final HttpServerResponse httpResponse;
  private final String transcodingResponseBody;

  public TranscodingGrpcServerResponse(ContextInternal context, GrpcServerRequestImpl<Req, Resp> request, GrpcProtocol protocol, HttpServerResponse httpResponse, String transcodingResponseBody, GrpcMessageEncoder<Resp> encoder) {
    super(context, request, protocol, httpResponse, encoder);

    this.request = (TranscodingGrpcServerRequest<Req, Resp>) request;
    this.httpResponse = httpResponse;
    this.transcodingResponseBody = transcodingResponseBody;
  }

  private Future<Void> sendTranscodedMessage(Buffer message) {
    try {
      BufferInternal transcoded = (BufferInternal) MessageWeaver.weaveResponseMessage(message, transcodingResponseBody);
      httpResponse.putHeader("content-length", Integer.toString(message.length()));
      httpResponse.putHeader("content-type", GrpcProtocol.HTTP_1.mediaType());
      return httpResponse.write(transcoded);
    } catch (Exception e) {
      httpResponse.setStatusCode(500).end();
      return Future.failedFuture(e);
    }
  }

  @Override
  protected Future<Void> sendMessage(Buffer message, boolean compressed) {
    if (TranscodingGrpcServerRequest.isTranscodable(request.httpRequest)) {
      return sendTranscodedMessage(message);
    }
    return super.sendMessage(message, compressed);
  }

  @Override
  protected void sendCancel() {
    httpResponse.setStatusCode(400);
    httpResponse.end();
  }
}
