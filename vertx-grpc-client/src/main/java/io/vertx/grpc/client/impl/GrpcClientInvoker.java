package io.vertx.grpc.client.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

import java.time.Duration;

public interface GrpcClientInvoker {

  ContextInternal context();

  default GrpcStream invoke(ServiceName serviceName, String methodName) {
    return invoke(serviceName, methodName, null);
  }

  GrpcStream invoke(ServiceName serviceName, String methodName, Duration idleTimeout);

}
