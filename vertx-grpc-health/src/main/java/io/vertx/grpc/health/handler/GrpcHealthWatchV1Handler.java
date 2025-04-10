package io.vertx.grpc.health.handler;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.health.v1.HealthCheckRequest;
import io.vertx.grpc.health.v1.HealthCheckResponse;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GrpcHealthWatchV1Handler extends GrpcHealthV1HandlerBase implements Handler<GrpcServerRequest<HealthCheckRequest, HealthCheckResponse>>, Closeable {

  private static final Logger logger = Logger.getLogger(GrpcHealthWatchV1Handler.class.getName());

  public static final ServiceMethod<HealthCheckRequest, HealthCheckResponse> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("grpc.health.v1.Health"),
    "Watch",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(HealthCheckRequest.parser()));

  private final Map<String, Map<GrpcServerResponse<HealthCheckRequest, HealthCheckResponse>, Boolean>> watchers = new ConcurrentHashMap<>();
  private final Vertx vertx;

  private long timerId = -1;

  public GrpcHealthWatchV1Handler(Vertx vertx, GrpcServer server, Map<String, Supplier<Future<Boolean>>> healthChecks) {
    super(server, healthChecks);

    this.vertx = vertx;
    this.timerId = vertx.setPeriodic(2500, id -> checkHealthStatusChanges());
  }

  private void checkHealthStatusChanges() {
    // For each service with watchers, check its status and notify watchers if changed
    for (String service : watchers.keySet()) {
      checkStatus(service).onSuccess(result -> {
        HealthCheckResponse.Builder builder = HealthCheckResponse.newBuilder();
        builder.setStatus(statusToProto(result));
        HealthCheckResponse response = builder.build();

        Map<GrpcServerResponse<HealthCheckRequest, HealthCheckResponse>, Boolean> serviceWatchers = watchers.get(service);
        if (serviceWatchers != null) {
          for (GrpcServerResponse<HealthCheckRequest, HealthCheckResponse> watcher : serviceWatchers.keySet()) {
            watcher.write(response);
          }
        }
      }).onFailure(failure ->
        logger.log(Level.WARNING, "Failed to check health status for service: " + service, failure)
      );
    }
  }

  @Override
  public void handle(GrpcServerRequest<HealthCheckRequest, HealthCheckResponse> request) {
    request.handler(check -> {
      final String service = check.getService();
      final GrpcServerResponse<HealthCheckRequest, HealthCheckResponse> response = request.response();

      // Send initial status
      checkStatus(service).onSuccess(result -> {
        HealthCheckResponse.Builder builder = HealthCheckResponse.newBuilder();
        HealthCheckResponse.ServingStatus status = statusToProto(result);
        builder.setStatus(status);
        response.write(builder.build());

        // Add to watchers
        watchers.computeIfAbsent(service, k -> new ConcurrentHashMap<>()).put(response, Boolean.TRUE);

        // Handle client disconnection
        request.connection().closeHandler(v -> removeWatcher(service, response));
        request.exceptionHandler(e -> removeWatcher(service, response));
      }).onFailure(failure -> {
        // If service is unknown, send SERVICE_UNKNOWN but don't end the stream
        HealthCheckResponse.Builder builder = HealthCheckResponse.newBuilder();
        builder.setStatus(HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN);
        response.write(builder.build());

        // Add to watchers
        watchers.computeIfAbsent(service, k -> new ConcurrentHashMap<>()).put(response, Boolean.TRUE);

        // Handle client disconnection
        request.connection().closeHandler(v -> removeWatcher(service, response));
        request.exceptionHandler(e -> removeWatcher(service, response));
      });
    });
  }

  private void removeWatcher(String service, GrpcServerResponse<HealthCheckRequest, HealthCheckResponse> response) {
    Map<GrpcServerResponse<HealthCheckRequest, HealthCheckResponse>, Boolean> serviceWatchers = watchers.get(service);
    if (serviceWatchers != null) {
      serviceWatchers.remove(response);
      if (serviceWatchers.isEmpty()) {
        watchers.remove(service);
      }
    }
  }

  @Override
  public void close() {
    if (timerId != -1) {
      vertx.cancelTimer(timerId);
      timerId = -1;
    }
    watchers.clear();
  }
}
