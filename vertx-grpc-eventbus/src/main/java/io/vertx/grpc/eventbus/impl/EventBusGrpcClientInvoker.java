package io.vertx.grpc.eventbus.impl;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.MethodType;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final EventBus eventBus;
  private final MethodType type;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBus eventBus, MethodType type) {
    this.context = context;
    this.eventBus = eventBus;
    this.type = type;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    if (type.streaming()) {
      return new EventBusGrpcClientStreamingCall(context, eventBus, serviceName, methodName);
    }
    return new EventBusGrpcClientUnaryCall(context, eventBus, serviceName, methodName);
  }
}
