package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.internal.CleanableResource;
import io.vertx.core.internal.CloseableResource;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.EventBusGrpcClient;

import java.lang.ref.Cleaner;
import java.time.Duration;

public class CleanableEventBusGrpcClient extends CleanableResource<EventBusGrpcClientEndpoint> implements EventBusGrpcClient {

  CleanableEventBusGrpcClient(Cleaner cleaner, CloseableResource<EventBusGrpcClientEndpoint> dispose) {
    super(cleaner, dispose);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method) {
    return getOrDie().request(method);
  }

  public EventBusGrpcClientEndpoint unwrap() {
    return get();
  }

  public Future<GrpcClientInvoker> connect(ServiceMethod<?, ?> method) {
    return getOrDie().connect(method);
  }

  @Override
  public Future<Void> close() {
    return shutdown(Duration.ZERO);
  }
}
