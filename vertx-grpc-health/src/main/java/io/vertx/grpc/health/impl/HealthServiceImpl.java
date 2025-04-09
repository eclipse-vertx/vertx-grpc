package io.vertx.grpc.health.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.health.HealthService;
import io.vertx.grpc.health.handler.GrpcHealthCheckV1Handler;
import io.vertx.grpc.health.handler.GrpcHealthWatchV1Handler;
import io.vertx.grpc.health.v1.HealthProto;
import io.vertx.grpc.server.GrpcServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class HealthServiceImpl implements HealthService {

  private static final ServiceName V1_SERVICE_NAME = ServiceName.create("grpc.health.v1.Health");
  private static final Descriptors.ServiceDescriptor V1_SERVICE_DESCRIPTOR = HealthProto.getDescriptor().findServiceByName("Health");

  private final Vertx vertx;
  private final Map<String, Supplier<Future<Boolean>>> checks = new ConcurrentHashMap<>();

  public HealthServiceImpl(Vertx vertx) {
    this.vertx = vertx;
    this.register(V1_SERVICE_NAME, () -> Future.succeededFuture(true));
  }

  @Override
  public ServiceName name() {
    return V1_SERVICE_NAME;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return V1_SERVICE_DESCRIPTOR;
  }

  @Override
  public void bind(GrpcServer server) {
    server.callHandler(GrpcHealthCheckV1Handler.SERVICE_METHOD, new GrpcHealthCheckV1Handler(server, checks));
    server.callHandler(GrpcHealthWatchV1Handler.SERVICE_METHOD, new GrpcHealthWatchV1Handler(vertx, server, checks));
  }

  @Override
  public HealthService register(String name, Supplier<Future<Boolean>> check) {
    checks.put(name, check);
    return this;
  }

  @Override
  public HealthService unregister(String name) {
    checks.remove(name);
    return this;
  }

  @Override
  public Future<Boolean> checkStatus(String name) {
    Promise<Boolean> promise = Promise.promise();
    Supplier<Future<Boolean>> check = checks.get(name);
    if (check != null) {
      check.get().onComplete(promise);
    } else {
      promise.fail("Unknown service " + name);
    }
    return promise.future();
  }
}
