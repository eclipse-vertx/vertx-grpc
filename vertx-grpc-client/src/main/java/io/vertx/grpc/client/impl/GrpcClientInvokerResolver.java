package io.vertx.grpc.client.impl;

import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcOutboundInvoker;

public interface GrpcClientInvokerResolver {

  // For now synchronous, but should become async
  Http2GrpcInboundInvoker resolveInvoker(ServiceName serviceName, String methodName);

}
