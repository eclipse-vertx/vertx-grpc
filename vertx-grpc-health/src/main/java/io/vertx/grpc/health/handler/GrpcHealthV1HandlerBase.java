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
    server.services().forEach(service -> {
      if (!checks.containsKey(service.name().fullyQualifiedName())) {
        checks.put(service.name().fullyQualifiedName(), () -> Future.succeededFuture(true));
      }
    });
    return checks;
  }

  protected Future<HealthCheckResponse.ServingStatus> checkStatus(String name) {
    // Default server status
    if (name == null || name.isBlank()) {
      return Future.succeededFuture(HealthCheckResponse.ServingStatus.SERVING);
    }

    Supplier<Future<Boolean>> check = healthChecks.get(name);
    if (check != null) {
      return check.get().map(this::statusToProto);
    } else {
      if (server.services().stream().anyMatch(service -> service.name().fullyQualifiedName().equals(name))) {
        return Future.succeededFuture(HealthCheckResponse.ServingStatus.SERVING);
      }

      return Future.succeededFuture(HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN);
    }
  }

  private HealthCheckResponse.ServingStatus statusToProto(boolean status) {
    return status ? HealthCheckResponse.ServingStatus.SERVING : HealthCheckResponse.ServingStatus.NOT_SERVING;
  }
}
