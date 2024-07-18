/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.iogrpc.client.impl;

import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.impl.GrpcClientImpl;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.impl.GrpcRequestLocal;
import io.vertx.iogrpc.client.IoGrpcClient;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class IoGrpcClientImpl extends GrpcClientImpl implements IoGrpcClient {

  public IoGrpcClientImpl(Vertx vertx, GrpcClientOptions grpcOptions, HttpClientOptions httpOptions) {
    super(vertx, grpcOptions, httpOptions);
  }

  public IoGrpcClientImpl(Vertx vertx) {
    super(vertx);
  }

  public IoGrpcClientImpl(Vertx vertx, HttpClient client) {
    super(vertx, client);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(MethodDescriptor<Req, Resp> service) {
    GrpcMessageDecoder<Resp> messageDecoder = GrpcMessageDecoder.unmarshaller(service.getResponseMarshaller());
    GrpcMessageEncoder<Req> messageEncoder = GrpcMessageEncoder.marshaller(service.getRequestMarshaller());
    return request(messageDecoder, messageEncoder).map(req -> req.fullMethodName(service.getFullMethodName()));
  }

  @Override public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(Address server, MethodDescriptor<Req, Resp> service) {
    GrpcMessageDecoder<Resp> messageDecoder = GrpcMessageDecoder.unmarshaller(service.getResponseMarshaller());
    GrpcMessageEncoder<Req> messageEncoder = GrpcMessageEncoder.marshaller(service.getRequestMarshaller());
    return request(server, messageDecoder, messageEncoder).map(req -> req.fullMethodName(service.getFullMethodName()));
  }
}