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
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.common.impl.GrpcWriteStreamBase;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.transcoding.HttpVariableBinding;
import io.vertx.grpc.transcoding.MessageWeaver;

import java.util.List;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class TranscodingGrpcServerRequest<Req, Resp> extends GrpcServerRequestImpl<Req, Resp> {

  public static boolean isTranscodable(HttpServerRequest httpRequest) {
    if (httpRequest == null) {
      return false;
    }

    return (httpRequest.version() == HttpVersion.HTTP_1_0 ||
            httpRequest.version() == HttpVersion.HTTP_1_1) &&
            GrpcProtocol.HTTP_1.mediaType().equals(httpRequest.getHeader(CONTENT_TYPE));
  }

  final String transcodingRequestBody;
  final List<HttpVariableBinding> bindings;
  private TranscodingGrpcServerResponse<Req, Resp> response;

  public TranscodingGrpcServerRequest(ContextInternal context, boolean scheduleDeadline, GrpcProtocol protocol, WireFormat format, long maxMessageSize, HttpServerRequest httpRequest, String transcodingRequestBody, List<HttpVariableBinding> bindings, GrpcMessageDecoder<Req> messageDecoder, GrpcMethodCall methodCall) {
    super(context, scheduleDeadline, protocol, format, httpRequest, new TranscodingMessageDeframer(transcodingRequestBody, bindings), new GrpcMessageDecoder<>() {
      @Override
      public Req decode(GrpcMessage msg) throws CodecException {
        Buffer transcoded;
        try {
          transcoded = MessageWeaver.weaveRequestMessage(msg.payload(), bindings, transcodingRequestBody);
        } catch (DecodeException e) {
          throw new CodecException(e);
        }
        return messageDecoder.decode(GrpcMessage.message("identity", transcoded));
      }
      @Override
      public WireFormat format() {
        return messageDecoder.format();
      }
    }, methodCall);

    this.transcodingRequestBody = transcodingRequestBody;
    this.bindings = bindings;
  }

  @Override
  public void init(GrpcWriteStreamBase ws) {
    this.response = (TranscodingGrpcServerResponse<Req, Resp>) ws;
    super.init(ws);
  }
}
