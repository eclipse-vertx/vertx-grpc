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
package io.vertx.grpc.client.impl;

import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpcio.client.GrpcIoClient;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcClientImpl implements GrpcIoClient {

  private final Vertx vertx;
  private HttpClient client;
  private final long maxMessageSize;

  public GrpcClientImpl(Vertx vertx, GrpcClientOptions options) {
    HttpClientOptions transportOptions = options.getTransportOptions();
    if (transportOptions == null) {
      transportOptions = new HttpClientOptions().setHttp2ClearTextUpgrade(false);
    } else {
      transportOptions = new HttpClientOptions(transportOptions);
    }
    transportOptions.setProtocolVersion(HttpVersion.HTTP_2);

    this.vertx = vertx;
    this.client = vertx.createHttpClient(transportOptions);
    this.maxMessageSize = options.getMaxMessageSize();
  }

  @Override public Future<GrpcClientRequest<Buffer, Buffer>> request(SocketAddress server) {
    RequestOptions options = new RequestOptions()
      .setMethod(HttpMethod.POST)
      .setServer(server);
    return client.request(options)
      .map(request -> new GrpcClientRequestImpl<>(request, maxMessageSize, GrpcMessageEncoder.IDENTITY, GrpcMessageDecoder.IDENTITY));
  }

  @Override public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(SocketAddress server, MethodDescriptor<Req, Resp> service) {
    GrpcMessageDecoder<Resp> messageDecoder = GrpcMessageDecoder.unmarshaller(service.getResponseMarshaller());
    GrpcMessageEncoder<Req> messageEncoder = GrpcMessageEncoder.marshaller(service.getRequestMarshaller());
    return request(server, ServiceMethod.client(ServiceName.create(service.getServiceName()), service.getBareMethodName(), messageEncoder, messageDecoder));
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(SocketAddress server, ServiceMethod<Resp, Req> method) {
    return request(new RequestOptions()
      .setMethod(HttpMethod.POST)
      .setServer(server), method);
  }

  private <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(RequestOptions options, ServiceMethod<Resp, Req> method) {
    GrpcMessageDecoder<Resp> messageDecoder = method.decoder();
    GrpcMessageEncoder<Req> messageEncoder = method.encoder();
    return client.request(options)
      .map(request -> {
        GrpcClientRequestImpl<Req, Resp> call = new GrpcClientRequestImpl<>(request, maxMessageSize, messageEncoder, messageDecoder);
        call.fullMethodName(method.fullMethodName());
        return call;
      });
  }

  @Override
  public Future<Void> close() {
    return client.close();
  }
}
