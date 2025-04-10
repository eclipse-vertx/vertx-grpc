package io.vertx.grpc.health.handler;

import io.vertx.core.Future;
import io.vertx.grpc.health.v1.HealthCheckResponse;
import io.vertx.grpc.server.GrpcServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public abstract class GrpcHealthV1HandlerBase {

  protected final GrpcServer server;
  protected final Map<String, Supplier<Future<Boolean>>> healthChecks;

  protected GrpcHealthV1HandlerBase(GrpcServer server, Map<String, Supplier<Future<Boolean>>> healthChecks) {
    this.server = server;
    this.healthChecks = healthChecks;
  }

  protected Map<String, Supplier<Future<Boolean>>> healthChecks() {
    Map<String, Supplier<Future<Boolean>>> checks = new ConcurrentHashMap<>(healthChecks);
    server.getServices().forEach(service -> {
      if (!checks.containsKey(service.name().fullyQualifiedName())) {
        checks.put(service.name().fullyQualifiedName(), () -> Future.succeededFuture(true));
      }
    });
    return checks;
  }

  protected Future<Boolean> checkStatus(String name) {
    Supplier<Future<Boolean>> check = healthChecks.get(name);
    if (check != null) {
      return check.get();
    } else {
      if (server.getServices().stream().anyMatch(service -> service.name().fullyQualifiedName().equals(name))) {
        return Future.succeededFuture(true);
      }

      return Future.failedFuture("Unknown service " + name);
    }
  }

  protected HealthCheckResponse.ServingStatus statusToProto(boolean status) {
    return status ? HealthCheckResponse.ServingStatus.SERVING : HealthCheckResponse.ServingStatus.NOT_SERVING;
  }
}
