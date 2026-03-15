package io.vertx.grpc.client.impl;

import io.vertx.grpc.common.ServiceName;

public interface GrpcClientInvoker {

  Http2GrpcInboundStream invoke(ServiceName serviceName, String methodName);

}
