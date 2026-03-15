package io.vertx.grpc.client.impl;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcOutboundInvoker;

public class Http2GrpcClientInvokerResolver implements GrpcClientInvokerResolver {

  private final HttpClientRequest httpRequest;
  private final ContextInternal context;
  private final long maxMessageSize;

  public Http2GrpcClientInvokerResolver(HttpClientRequest httpRequest, long maxMessageSize) {
    this.httpRequest = httpRequest;
    this.context = ((PromiseInternal<?>)httpRequest.response()).context();
    this.maxMessageSize = maxMessageSize;
  }

  @Override
  public Http2GrpcInboundInvoker resolveInvoker(ServiceName serviceName, String methodName) {
    return new Http2GrpcInboundInvoker(context, httpRequest, serviceName, methodName, maxMessageSize);
  }
}
