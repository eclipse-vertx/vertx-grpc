package io.vertx.grpc.health.handler;

import io.vertx.core.Handler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.grpc.health.v1.HealthCheckRequest;
import io.vertx.grpc.health.v1.HealthCheckResponse;
import io.vertx.grpc.server.GrpcServerRequest;

public abstract class GrpcHealthV1HandlerBase implements Handler<GrpcServerRequest<HealthCheckRequest, HealthCheckResponse>> {

  protected final HealthChecks healthChecks;

  protected GrpcHealthV1HandlerBase(HealthChecks healthChecks) {
    this.healthChecks = healthChecks;
  }

  protected HealthCheckResponse.ServingStatus statusToProto(Status status) {
    if (status.isOk()) {
      return HealthCheckResponse.ServingStatus.SERVING;
    }

    return HealthCheckResponse.ServingStatus.NOT_SERVING;
  }
}
