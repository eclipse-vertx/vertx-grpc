package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;

public class EventBusGrpcClientImpl extends EventBusStreamEndpoint implements EventBusGrpcClient {

  private final WireFormat wireFormat;

  private EventBusGrpcClientImpl(Vertx vertx, EventBus eventBus, EventBusGrpcClientOptions options) {
    super(vertx, eventBus, "grpc.eb.client.");
    this.wireFormat = options.getWireFormat();
  }

  public static Future<EventBusGrpcClient> create(Vertx vertx, EventBus eventBus, EventBusGrpcClientOptions options) {
    EventBusGrpcClientImpl client = new EventBusGrpcClientImpl(vertx, eventBus, options);
    return client.bind().map(client);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> invoker(ServiceMethod<Resp, Req> method) {
    return request(method);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method) {
    EventBusGrpcClientInvoker invoker = new EventBusGrpcClientInvoker(context(), this, method.clientStreaming() || method.serverStreaming());
    GrpcClientRequestImpl<Req, Resp> request = new GrpcClientRequestImpl<>(
      context(),
      invoker,
      false,
      method.encoder(),
      method.decoder()
    );

    request.serviceName(method.serviceName());
    request.methodName(method.methodName());
    request.format(wireFormat);
    return context().succeededFuture(request);
  }
}
