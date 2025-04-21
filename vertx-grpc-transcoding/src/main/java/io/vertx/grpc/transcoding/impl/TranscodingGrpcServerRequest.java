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
package io.vertx.grpc.transcoding.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.impl.GrpcServerRequestImpl;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;

import java.util.List;

public class TranscodingGrpcServerRequest<Req, Resp> extends GrpcServerRequestImpl<Req, Resp> {

  final String transcodingRequestBody;
  final List<HttpVariableBinding> bindings;

  public TranscodingGrpcServerRequest(ContextInternal context, HttpServerRequest httpRequest, String transcodingRequestBody, List<HttpVariableBinding> bindings, GrpcMessageDecoder<Req> messageDecoder, GrpcMethodCall methodCall) {
    super(context, GrpcProtocol.TRANSCODING, WireFormat.JSON, httpRequest, new TranscodingMessageDeframer(), new GrpcMessageDecoder<>() {
      @Override
      public Req decode(GrpcMessage msg) throws CodecException {
        Buffer transcoded;
        try {
          transcoded = MessageWeaver.weaveRequestMessage(msg.payload(), bindings, transcodingRequestBody);
        } catch (DecodeException e) {
          throw new CodecException(e);
        }
        return messageDecoder.decode(GrpcMessage.message("identity", WireFormat.JSON, transcoded));
      }
      @Override
      public boolean accepts(WireFormat format) {
        return messageDecoder.accepts(format);
      }
    }, methodCall);

    this.transcodingRequestBody = transcodingRequestBody;
    this.bindings = bindings;
  }
}
