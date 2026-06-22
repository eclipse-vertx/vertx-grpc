package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.EventBusGrpcClient;

public class EventBusGrpcClientImpl extends EventBusStreamEndpoint implements EventBusGrpcClient {

  private EventBusGrpcClientImpl(Vertx vertx, EventBus eventBus) {
    super(vertx, eventBus, "grpc.eb.client.");
  }

  public static Future<EventBusGrpcClient> create(Vertx vertx, EventBus eventBus) {
    EventBusGrpcClientImpl client = new EventBusGrpcClientImpl(vertx, eventBus);
    return client.bind().map(client);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> invoker(ServiceMethod<Resp, Req> method) {
    return request(method);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method) {
    EventBusGrpcClientInvoker invoker = new EventBusGrpcClientInvoker(context(), this, method.type());
    GrpcClientRequestImpl<Req, Resp> request = new GrpcClientRequestImpl<>(
      context(),
      invoker,
      false,
      method.encoder(),
      method.decoder()
    );

    request.serviceName(method.serviceName());
    request.methodName(method.methodName());
    return context().succeededFuture(request);
  }
}
