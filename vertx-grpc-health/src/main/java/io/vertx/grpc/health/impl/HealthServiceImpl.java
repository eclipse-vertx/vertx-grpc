package io.vertx.grpc.health.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.health.HealthService;
import io.vertx.grpc.health.handler.GrpcHealthCheckV1Handler;
import io.vertx.grpc.health.handler.GrpcHealthWatchV1Handler;
import io.vertx.grpc.health.v1.HealthProto;
import io.vertx.grpc.server.GrpcServer;

public class HealthServiceImpl implements HealthService {

  private static final ServiceName V1_SERVICE_NAME = ServiceName.create("grpc.health.v1.Health");
  private static final Descriptors.ServiceDescriptor V1_SERVICE_DESCRIPTOR = HealthProto.getDescriptor().findServiceByName("Health");

  private final HealthChecks healthChecks;
  private final Vertx vertx;

  public HealthServiceImpl(Vertx vertx, HealthChecks healthChecks) {
    this.vertx = vertx;
    this.healthChecks = healthChecks;
    this.healthChecks.register(V1_SERVICE_NAME.fullyQualifiedName(), promise -> promise.complete(Status.OK()));
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
    server.callHandler(GrpcHealthCheckV1Handler.SERVICE_METHOD, new GrpcHealthCheckV1Handler(healthChecks));
    server.callHandler(GrpcHealthWatchV1Handler.SERVICE_METHOD, new GrpcHealthWatchV1Handler(vertx, healthChecks));
  }

  @Override
  public HealthChecks register(String s, Handler<Promise<Status>> handler) {
    return healthChecks.register(s, handler);
  }

  @Override
  public HealthChecks register(String s, long l, Handler<Promise<Status>> handler) {
    return healthChecks.register(s, l, handler);
  }

  @Override
  public HealthChecks unregister(String s) {
    return healthChecks.unregister(s);
  }

  @Override
  public HealthChecks invoke(Handler<JsonObject> handler) {
    return healthChecks.invoke(handler);
  }

  @Override
  public Future<JsonObject> invoke(String s) {
    return healthChecks.invoke(s);
  }

  @Override
  public Future<CheckResult> checkStatus() {
    return healthChecks.checkStatus();
  }

  @Override
  public Future<CheckResult> checkStatus(String s) {
    return healthChecks.checkStatus(s);
  }
}
