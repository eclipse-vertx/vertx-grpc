package io.vertx.grpc.client.impl;

import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcInvoker;

public interface GrpcClientInvokerResolver {

  // For now synchronous, but should become async
  GrpcInvoker resolveInvoker(ServiceName serviceName, String methodName);

}
