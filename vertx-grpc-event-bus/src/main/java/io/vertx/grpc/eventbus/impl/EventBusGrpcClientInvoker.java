package io.vertx.grpc.eventbus.impl;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final EventBus eventBus;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBus eventBus) {
    this.context = context;
    this.eventBus = eventBus;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    return new EventBusGrpcStream(context, eventBus, serviceName, methodName);
  }
}
