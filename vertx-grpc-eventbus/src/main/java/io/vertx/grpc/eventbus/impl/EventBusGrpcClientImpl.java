package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.EventBusGrpcClient;

public class EventBusGrpcClientImpl implements EventBusGrpcClient {

  private final Vertx vertx;
  private final EventBus eventBus;

  public EventBusGrpcClientImpl(Vertx vertx, EventBus eventBus) {
    this.vertx = vertx;
    this.eventBus = eventBus;
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> invoker(ServiceMethod<Resp, Req> method) {
    return request(method);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    EventBusGrpcClientInvoker invoker = new EventBusGrpcClientInvoker(context, eventBus);
    GrpcClientRequestImpl<Req, Resp> request = new GrpcClientRequestImpl<>(
      context,
      invoker,
      false,
      method.encoder(),
      method.decoder()
    );

    request.serviceName(method.serviceName());
    request.methodName(method.methodName());
    return context.succeededFuture(request);
  }
}
