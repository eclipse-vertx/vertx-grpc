package io.vertx.grpc.eventbus.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final boolean remoteUnary;
  private final EventBusGrpcClientEndpoint client;
  private final boolean localUnary;
  private final int initialInboundWindowSize;
  private final int initialOutboundWindowSize;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBusGrpcClientEndpoint client, boolean localUnary,
                                   boolean remoteUnary, int initialInboundWindowSize, int initialOutboundWindowSize) {
    this.client = client;
    this.context = context;
    this.localUnary = localUnary;
    this.remoteUnary = remoteUnary;
    this.initialInboundWindowSize = initialInboundWindowSize;
    this.initialOutboundWindowSize = initialOutboundWindowSize;
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName) {
    return new EventBusGrpcClientCall(client, client.nextStreamId(), context, localUnary, remoteUnary, serviceName, methodName,
      initialInboundWindowSize, initialOutboundWindowSize);
  }
}
