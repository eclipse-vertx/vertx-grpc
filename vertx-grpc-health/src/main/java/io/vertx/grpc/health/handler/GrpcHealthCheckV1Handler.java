package io.vertx.grpc.health.handler;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.grpc.common.*;
import io.vertx.grpc.health.v1.HealthCheckRequest;
import io.vertx.grpc.health.v1.HealthCheckResponse;
import io.vertx.grpc.server.ServiceContainer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.ServiceMethodInvoker;

import java.util.Map;
import java.util.function.Supplier;

public class GrpcHealthCheckV1Handler extends GrpcHealthV1HandlerBase implements ServiceMethodInvoker<HealthCheckRequest, HealthCheckResponse> {

  public static final ServiceMethod<HealthCheckRequest, HealthCheckResponse> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("grpc.health.v1.Health"),
    "Check",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(HealthCheckRequest.newBuilder()));

  public GrpcHealthCheckV1Handler(ServiceContainer server, Map<String, Supplier<Future<Boolean>>> healthChecks) {
    super(server, healthChecks);
  }

  @Override
  public void invoke(GrpcServerRequest<HealthCheckRequest, HealthCheckResponse> request) {
    request.handler(check -> checkStatus(check.getService()).compose(result -> {
      if (result == HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN) {
        return request.response().status(GrpcStatus.NOT_FOUND).end();
      }

      HealthCheckResponse.Builder builder = HealthCheckResponse.newBuilder();
      builder.setStatus(result);
      return request.response().end(builder.build());
    }).onFailure(failure -> request.response().status(GrpcStatus.INTERNAL).end()));
  }
}
