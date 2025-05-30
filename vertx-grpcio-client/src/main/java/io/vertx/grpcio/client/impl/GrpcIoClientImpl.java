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
package io.vertx.grpcio.client.impl;

import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.Address;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.client.impl.GrpcClientImpl;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.common.impl.BridgeMessageDecoder;
import io.vertx.grpcio.common.impl.BridgeMessageEncoder;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcIoClientImpl extends GrpcClientImpl implements GrpcIoClient {

  public GrpcIoClientImpl(Vertx vertx, HttpClient client) {
    super(vertx, client);
  }

  public GrpcIoClientImpl(Vertx vertx, GrpcClientOptions grpcOptions, HttpClient client, boolean close) {
    super(vertx, grpcOptions, client, close);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(MethodDescriptor<Req, Resp> service) {
    GrpcMessageDecoder<Resp> messageDecoder = new BridgeMessageDecoder<>(service.getResponseMarshaller(), null);
    GrpcMessageEncoder<Req> messageEncoder = new BridgeMessageEncoder<>(service.getRequestMarshaller(), null);
    ServiceMethod<Resp, Req> serviceMethod = ServiceMethod.client(ServiceName.create(service.getServiceName()), service.getBareMethodName(), messageEncoder, messageDecoder);
    return request(serviceMethod);
  }

  @Override public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(Address server, MethodDescriptor<Req, Resp> service) {
    GrpcMessageDecoder<Resp> messageDecoder = new BridgeMessageDecoder<>(service.getResponseMarshaller(), null);
    GrpcMessageEncoder<Req> messageEncoder = new BridgeMessageEncoder<>(service.getRequestMarshaller(), null);
    ServiceMethod<Resp, Req> serviceMethod = ServiceMethod.client(ServiceName.create(service.getServiceName()), service.getBareMethodName(), messageEncoder, messageDecoder);
    return request(server, serviceMethod);
  }
}
