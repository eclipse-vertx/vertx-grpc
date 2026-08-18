package io.vertx.grpc.eventbus.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final boolean remoteUnary;
  private final EventBusGrpcClientImpl client;
  private final boolean localUnary;
  private final int initialInboundWindowSize;
  private final int initialOutboundWindowSize;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBusGrpcClientImpl client, boolean localUnary,
                                   boolean remoteUnary, int initialInboundWindowSize, int initialOutboundWindowSize) {
    this.client = client;
    this.context = context;
    this.localUnary = localUnary;
    this.remoteUnary = remoteUnary;
    this.initialInboundWindowSize = initialInboundWindowSize;
    this.initialOutboundWindowSize = initialOutboundWindowSize;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    EventBusGrpcEndpoint.StreamRegistration registration = client.createStream();
    return new EventBusGrpcClientCall(context, localUnary, remoteUnary, registration, client, serviceName, methodName,
      initialInboundWindowSize, initialOutboundWindowSize);
  }
}
