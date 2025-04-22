package io.vertx.grpc.health.handler;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.health.v1.HealthCheckResponse;
import io.vertx.grpc.health.v1.HealthListRequest;
import io.vertx.grpc.health.v1.HealthListResponse;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.Map;
import java.util.function.Supplier;

public class GrpcHealthListV1Handler extends GrpcHealthV1HandlerBase implements Handler<GrpcServerRequest<HealthListRequest, HealthListResponse>> {

  public static final ServiceMethod<HealthListRequest, HealthListResponse> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("grpc.health.v1.Health"),
    "List",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(HealthListRequest.newBuilder()));

  public GrpcHealthListV1Handler(GrpcServer server, Map<String, Supplier<Future<Boolean>>> healthChecks) {
    super(server, healthChecks);
  }

  @Override
  public void handle(GrpcServerRequest<HealthListRequest, HealthListResponse> event) {
    event.handler(request -> {
      HealthListResponse.Builder builder = HealthListResponse.newBuilder();
      healthChecks().forEach((name, check) -> {
        HealthCheckResponse.Builder responseBuilder = HealthCheckResponse.newBuilder();
        checkStatus(name).onSuccess(result -> {
          responseBuilder.setStatus(result);
          builder.putStatuses(name, responseBuilder.build());
        }).onFailure(failure -> {
          responseBuilder.setStatus(HealthCheckResponse.ServingStatus.NOT_SERVING);
          builder.putStatuses(name, responseBuilder.build());
        });
      });
      event.response().end(builder.build());
    });
  }
}
