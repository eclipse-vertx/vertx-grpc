package io.vertx.grpc.eventbus.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final EventBusGrpcClientImpl client;
  private final boolean streaming;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBusGrpcClientImpl client, boolean streaming) {
    this.context = context;
    this.client = client;
    this.streaming = streaming;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    if (streaming) {
      return new EventBusGrpcClientStreamingCall(context, client, serviceName, methodName);
    }
    return new EventBusGrpcClientUnaryCall(context, client.eventBus(), serviceName, methodName);
  }
}
