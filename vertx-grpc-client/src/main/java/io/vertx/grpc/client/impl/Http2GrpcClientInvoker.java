package io.vertx.grpc.client.impl;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.grpc.common.ServiceName;

public class Http2GrpcClientInvoker implements GrpcClientInvoker {

  private final HttpClientRequest httpRequest;
  private final ContextInternal context;
  private final long maxMessageSize;

  public Http2GrpcClientInvoker(HttpClientRequest httpRequest, long maxMessageSize) {
    this.httpRequest = httpRequest;
    this.context = ((PromiseInternal<?>)httpRequest.response()).context();
    this.maxMessageSize = maxMessageSize;
  }

  @Override
  public Http2GrpcInboundStream invoke(ServiceName serviceName, String methodName) {
    return new Http2GrpcInboundStream(context, httpRequest, serviceName, methodName, maxMessageSize);
  }
}
