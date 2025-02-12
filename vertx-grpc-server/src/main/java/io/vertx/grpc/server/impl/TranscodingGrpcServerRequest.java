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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.common.impl.GrpcWriteStreamBase;
import io.vertx.grpc.common.impl.Http2GrpcMessageDeframer;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.transcoding.HttpVariableBinding;
import io.vertx.grpc.transcoding.MessageWeaver;

import java.util.List;

public class TranscodingGrpcServerRequest<Req, Resp> extends GrpcServerRequestImpl<Req, Resp> {

  final String transcodingRequestBody;
  final List<HttpVariableBinding> bindings;
  private TranscodingGrpcServerResponse<Req, Resp> response;

  public TranscodingGrpcServerRequest(ContextInternal context, boolean scheduleDeadline, GrpcProtocol protocol, WireFormat format, long maxMessageSize, HttpServerRequest httpRequest, String transcodingRequestBody, List<HttpVariableBinding> bindings, GrpcMessageDecoder<Req> messageDecoder, GrpcMethodCall methodCall) {
    super(context, scheduleDeadline, protocol, format, httpRequest, new Http2GrpcMessageDeframer(maxMessageSize, httpRequest.headers().get("grpc-encoding"), format), messageDecoder, methodCall);

    this.transcodingRequestBody = transcodingRequestBody;
    this.bindings = bindings;
  }

  @Override
  public void init(GrpcWriteStreamBase ws) {
    this.response = (TranscodingGrpcServerResponse<Req, Resp>) ws;
    super.init(ws);
  }

  @Override
  public void handle(Buffer chunk) {
    try {
      BufferInternal transcoded = (BufferInternal) MessageWeaver.weaveRequestMessage(chunk, bindings, transcodingRequestBody);
      if (transcoded == null) {
        return;
      }

      Buffer prefixed = BufferInternal.buffer(transcoded.length() + 5);

      prefixed.appendByte((byte) 0); // uncompressed flag
      prefixed.appendInt(transcoded.length()); // content length
      prefixed.appendBuffer(transcoded);

      chunk = prefixed;
    } catch (DecodeException e) {
      cancelTranscodable();
      httpRequest.response().setStatusCode(400);
      if (!httpRequest.response().ended()) {
        response.end();
      }
      return;
    }
    super.handle(chunk);
  }
}
