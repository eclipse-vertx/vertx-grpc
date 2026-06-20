package io.vertx.grpc.eventbus.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.MethodType;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final EventBusGrpcClientImpl client;
  private final MethodType type;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBusGrpcClientImpl client, MethodType type) {
    this.context = context;
    this.client = client;
    this.type = type;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    if (type.streaming()) {
      return new EventBusGrpcClientStreamingCall(context, client, serviceName, methodName);
    }
    return new EventBusGrpcClientUnaryCall(context, client.eventBus(), serviceName, methodName);
  }
}
