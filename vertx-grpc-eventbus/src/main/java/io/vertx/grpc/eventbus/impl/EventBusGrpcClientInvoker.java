package io.vertx.grpc.eventbus.impl;

import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.MethodCardinality;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

import java.time.Duration;

public class EventBusGrpcClientInvoker implements GrpcClientInvoker {

  private final ContextInternal context;
  private final boolean remoteUnary;
  private final EventBusGrpcClientEndpoint client;
  private final boolean localUnary;
  private final int initialInboundWindowSize;
  private final int initialOutboundWindowSize;

  public EventBusGrpcClientInvoker(ContextInternal context, EventBusGrpcClientEndpoint client, MethodCardinality cardinality,
                                   int initialInboundWindowSize, int initialOutboundWindowSize) {
    this.client = client;
    this.context = context;
    this.localUnary = cardinality == MethodCardinality.UNARY || cardinality == MethodCardinality.SERVER_STREAMING;
    this.remoteUnary = cardinality == MethodCardinality.UNARY || cardinality == MethodCardinality.CLIENT_STREAMING;
    this.initialInboundWindowSize = initialInboundWindowSize;
    this.initialOutboundWindowSize = initialOutboundWindowSize;
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public GrpcStream invoke(ServiceName serviceName, String methodName, Duration idleTimeout) {
    return new EventBusGrpcClientCall(client, client.nextStreamId(), context, localUnary, remoteUnary, serviceName, methodName,
      initialInboundWindowSize, initialOutboundWindowSize);
  }
}
