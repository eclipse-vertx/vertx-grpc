package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.ServiceResource;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;

import java.time.Duration;

class CleanableEventBusGrpcServer extends EventBusGrpcServerEndpoint {

  private final ServiceResource<Void, Void> serviceResource;
  private final ContextInternal consumerContext;

  CleanableEventBusGrpcServer(ContextInternal consumerContext, EventBusGrpcServerOptions options) {
    super(consumerContext, options);

    this.consumerContext = consumerContext;
    this.serviceResource = new ServiceResource<>() {
      @Override
      protected Future<Void> startImpl(ContextInternal context, Void args) {
        return CleanableEventBusGrpcServer.super.bind();
      }
      @Override
      protected Future<?> stopImpl(ContextInternal context, Void v, Duration timeout) {
        return CleanableEventBusGrpcServer.super.close();
      }
    };
  }

  @Override
  Future<Void> bind() {
    return serviceResource.start(consumerContext, null);
  }

  @Override
  public Future<Void> close() {
    return serviceResource.stop(consumerContext, Duration.ZERO);
  }
}
