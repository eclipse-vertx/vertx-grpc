package io.vertx.grpc.eventbus.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final EventBusGrpcClientImpl client;
  private final boolean streaming;
  private final long producerHeartbeat;
  private final long consumerIdleTimeout;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBusGrpcClientImpl client, boolean streaming, long producerHeartbeat, long consumerIdleTimeout) {
    this.context = context;
    this.client = client;
    this.streaming = streaming;
    this.producerHeartbeat = producerHeartbeat;
    this.consumerIdleTimeout = consumerIdleTimeout;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    if (streaming) {
      return new EventBusGrpcClientStreamingCall(context, client, serviceName, methodName, producerHeartbeat, consumerIdleTimeout);
    }
    return new EventBusGrpcClientUnaryCall(context, client.eventBus(), serviceName, methodName);
  }
}
