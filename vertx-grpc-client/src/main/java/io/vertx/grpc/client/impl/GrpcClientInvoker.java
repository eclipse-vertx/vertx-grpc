package io.vertx.grpc.client.impl;

import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public interface GrpcClientInvoker {

  GrpcStream invoke(ServiceName serviceName, String methodName);

}
