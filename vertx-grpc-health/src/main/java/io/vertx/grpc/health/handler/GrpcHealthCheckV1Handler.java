package io.vertx.grpc.health.handler;

import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.grpc.common.*;
import io.vertx.grpc.health.v1.HealthCheckRequest;
import io.vertx.grpc.health.v1.HealthCheckResponse;
import io.vertx.grpc.server.GrpcServerRequest;

public class GrpcHealthCheckV1Handler extends GrpcHealthV1HandlerBase {

  public static final ServiceMethod<HealthCheckRequest, HealthCheckResponse> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("grpc.health.v1.Health"),
    "Check",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(HealthCheckRequest.parser()));

  public GrpcHealthCheckV1Handler(HealthChecks healthChecks) {
    super(healthChecks);
  }

  @Override
  public void handle(GrpcServerRequest<HealthCheckRequest, HealthCheckResponse> request) {
    request.handler(check -> healthChecks.checkStatus(check.getService()).compose(result -> {
      HealthCheckResponse.Builder builder = HealthCheckResponse.newBuilder();
      builder.setStatus(statusToProto(result.getStatus()));
      return request.response().end(builder.build());
    }).onFailure(failure -> request.response().status(GrpcStatus.INTERNAL).end()));
  }
}
