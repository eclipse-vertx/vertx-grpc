package io.vertx.tests.client.eventbus;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;

  public EventBusGrpcClientInvoker(ContextInternal context) {
    this.context = context;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    return new EventBusGrpcStream(context, serviceName, methodName);
  }
}
