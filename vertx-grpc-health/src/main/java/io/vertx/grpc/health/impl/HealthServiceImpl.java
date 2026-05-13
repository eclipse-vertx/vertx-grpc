package io.vertx.grpc.health.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.health.HealthService;
import io.vertx.grpc.health.HealthServiceOptions;
import io.vertx.grpc.health.handler.GrpcHealthCheckV1Handler;
import io.vertx.grpc.health.handler.GrpcHealthListV1Handler;
import io.vertx.grpc.health.handler.GrpcHealthWatchV1Handler;
import io.vertx.grpc.health.v1.HealthProto;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.ServiceContainer;
import io.vertx.grpc.server.ServiceMethodInvoker;
import io.vertx.grpc.server.impl.ServerAware;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class HealthServiceImpl implements HealthService, ServerAware {

  private static final ServiceName V1_SERVICE_NAME = ServiceName.create("grpc.health.v1.Health");
  private static final Descriptors.ServiceDescriptor V1_SERVICE_DESCRIPTOR = HealthProto.getDescriptor().findServiceByName("Health");

  private final Vertx vertx;
  private final HealthServiceOptions options;
  private final Map<String, Supplier<Future<Boolean>>> checks = new ConcurrentHashMap<>();

  private ServiceContainer server;
  private ServiceMethodInvoker checkHandler;
  private ServiceMethodInvoker listHandler;
  private ServiceMethodInvoker watchHandler;

  public HealthServiceImpl(Vertx vertx) {
    this(vertx, new HealthServiceOptions());
  }

  public HealthServiceImpl(Vertx vertx, HealthServiceOptions options) {
    this.vertx = vertx;
    this.options = options;
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
  public List<ServiceMethod<?, ?>> methods() {
    return List.of(GrpcHealthCheckV1Handler.SERVICE_METHOD, GrpcHealthListV1Handler.SERVICE_METHOD, GrpcHealthWatchV1Handler.SERVICE_METHOD);
  }

  @Override
  public <Req, Resp> ServiceMethodInvoker<Req, Resp> invoker(ServiceMethod<Req, Resp> method) {
    ServiceMethodInvoker<Req, Resp> invoker;
    switch (method.methodName()) {
      case "Check":
        invoker = checkHandler;
        break;
      case "List":
        invoker = listHandler;
        break;
      case "Watch":
        invoker = watchHandler;
        break;
      default:
        invoker = HealthService.super.invoker(method);
        break;
    }
    return invoker;
  }

  @Override
  public void setServer(ServiceContainer server) {
    this.server = server;
    this.checkHandler = new GrpcHealthCheckV1Handler(server, checks);
    this.listHandler = new GrpcHealthListV1Handler(server, checks);
    this.watchHandler = new GrpcHealthWatchV1Handler(vertx, server, checks, options);
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
