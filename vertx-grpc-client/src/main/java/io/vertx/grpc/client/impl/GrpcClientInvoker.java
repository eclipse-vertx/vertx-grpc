package io.vertx.grpc.client.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public interface GrpcClientInvoker {

  ContextInternal context();

  GrpcStream invoke(ServiceName serviceName, String methodName);

}
