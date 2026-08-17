package io.vertx.grpc.eventbus.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final boolean remoteUnary;
  private final ContextInternal context;
  private final EventBusGrpcClientImpl client;
  private final boolean localUnary;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBusGrpcClientImpl client, boolean localUnary, boolean remoteUnary) {
    this.context = context;
    this.client = client;
    this.localUnary = localUnary;
    this.remoteUnary = remoteUnary;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    if (!localUnary || !remoteUnary) {
      EventBusGrpcEndpoint.StreamRegistration registration = client.createStream();
      return new EventBusGrpcClientStreamingCall(context, localUnary, remoteUnary, registration, client, serviceName, methodName);
    }
    return new EventBusGrpcClientUnaryCall(context, client.eventBus(), serviceName, methodName);
  }
}
