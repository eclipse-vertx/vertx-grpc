package io.vertx.grpc.health;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.health.impl.HealthServiceImpl;
import io.vertx.grpc.server.Service;

@VertxGen
public interface HealthService extends Service, HealthChecks {

  static HealthService create(Vertx vertx) {
    return new HealthServiceImpl(vertx, HealthChecks.create(vertx));
  }

  static HealthService create(Vertx vertx, HealthChecks healthChecks) {
    return new HealthServiceImpl(vertx, healthChecks);
  }

  default HealthChecks register(ServiceName serviceName, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName(), handler);
  }

  default HealthChecks register(ServiceName serviceName, String s, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName() + "/" + s, handler);
  }

  default HealthChecks register(ServiceName serviceName, long timeout, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName(), timeout, handler);
  }

  default HealthChecks register(ServiceName serviceName, String s, long timeout, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName() + "/" + s, timeout, handler);
  }

  default HealthChecks unregister(ServiceName serviceName) {
    return unregister(serviceName.fullyQualifiedName());
  }

  default Future<CheckResult> checkStatus(ServiceName serviceName) {
    return checkStatus(serviceName.fullyQualifiedName());
  }
}
