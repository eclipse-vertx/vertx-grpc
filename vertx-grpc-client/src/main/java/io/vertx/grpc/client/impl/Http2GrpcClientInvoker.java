package io.vertx.grpc.client.impl;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.grpc.common.ServiceName;

import java.time.Duration;

public class Http2GrpcClientInvoker implements GrpcClientInvoker {

  private final HttpClientRequest httpRequest;
  private final ContextInternal context;
  private final long maxMessageSize;

  public Http2GrpcClientInvoker(HttpClientRequest httpRequest, long maxMessageSize) {
    this.httpRequest = httpRequest;
    this.context = ((PromiseInternal<?>)httpRequest.response()).context();
    this.maxMessageSize = maxMessageSize;
  }

  public HttpConnection connection() {
    return httpRequest.connection();
  }

  @Override
  public ContextInternal context() {
    return ((PromiseInternal<?>)httpRequest.response()).context();
  }

  @Override
  public Http2GrpcInboundStream invoke(ServiceName serviceName, String methodName, Duration idleTimeout) {
    return new Http2GrpcInboundStream(context, httpRequest, serviceName, methodName, maxMessageSize, idleTimeout);
  }
}
