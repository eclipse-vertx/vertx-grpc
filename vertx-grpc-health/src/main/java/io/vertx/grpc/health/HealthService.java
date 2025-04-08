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

/**
 * A service that implements the standard gRPC health checking protocol.
 * <p>
 * This service provides a bridge between Vert.x's {@link HealthChecks} system and the gRPC health checking protocol.
 * It allows services to register health checks and have their health status queried via gRPC.
 * <p>
 * The service implements two RPCs as defined in the standard gRPC health protocol:
 * <ul>
 *   <li>Check: For checking the health status of a service</li>
 *   <li>Watch: For watching the health status of a service over time</li>
 * </ul>
 * <p>
 * Health status is reported using the standard gRPC health status values:
 * <ul>
 *   <li>UNKNOWN: Health status is unknown</li>
 *   <li>SERVING: Service is healthy and serving requests</li>
 *   <li>NOT_SERVING: Service is not serving requests</li>
 *   <li>SERVICE_UNKNOWN: The requested service is unknown (used only by the Watch method)</li>
 * </ul>
 */
@VertxGen
public interface HealthService extends Service, HealthChecks {

  /**
   * Creates a new HealthService instance with a new HealthChecks instance.
   *
   * @param vertx the Vert.x instance
   * @return a new HealthService instance
   */
  static HealthService create(Vertx vertx) {
    return new HealthServiceImpl(vertx, HealthChecks.create(vertx));
  }

  /**
   * Creates a new HealthService instance with the provided HealthChecks instance.
   *
   * @param vertx the Vert.x instance
   * @param healthChecks the HealthChecks instance to use
   * @return a new HealthService instance
   */
  static HealthService create(Vertx vertx, HealthChecks healthChecks) {
    return new HealthServiceImpl(vertx, healthChecks);
  }

  /**
   * Registers a health check procedure for the specified service.
   *
   * @param serviceName the service name to register the health check for
   * @param handler the handler that will be called to check the health status
   * @return a reference to this, so the API can be used fluently
   */
  default HealthChecks register(ServiceName serviceName, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName(), handler);
  }

  /**
   * Registers a health check procedure for the specified service and sub-component.
   *
   * @param serviceName the service name to register the health check for
   * @param s the sub-component name
   * @param handler the handler that will be called to check the health status
   * @return a reference to this, so the API can be used fluently
   */
  default HealthChecks register(ServiceName serviceName, String s, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName() + "/" + s, handler);
  }

  /**
   * Registers a health check procedure for the specified service with a timeout.
   *
   * @param serviceName the service name to register the health check for
   * @param timeout the timeout in milliseconds after which the check is considered failed
   * @param handler the handler that will be called to check the health status
   * @return a reference to this, so the API can be used fluently
   */
  default HealthChecks register(ServiceName serviceName, long timeout, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName(), timeout, handler);
  }

  /**
   * Registers a health check procedure for the specified service and sub-component with a timeout.
   *
   * @param serviceName the service name to register the health check for
   * @param s the sub-component name
   * @param timeout the timeout in milliseconds after which the check is considered failed
   * @param handler the handler that will be called to check the health status
   * @return a reference to this, so the API can be used fluently
   */
  default HealthChecks register(ServiceName serviceName, String s, long timeout, Handler<Promise<Status>> handler) {
    return register(serviceName.fullyQualifiedName() + "/" + s, timeout, handler);
  }

  /**
   * Unregisters a health check procedure for the specified service.
   *
   * @param serviceName the service name to unregister the health check for
   * @return a reference to this, so the API can be used fluently
   */
  default HealthChecks unregister(ServiceName serviceName) {
    return unregister(serviceName.fullyQualifiedName());
  }

  /**
   * Checks the health status of the specified service.
   *
   * @param serviceName the service name to check the health status for
   * @return a future that will be completed with the health check result
   */
  default Future<CheckResult> checkStatus(ServiceName serviceName) {
    return checkStatus(serviceName.fullyQualifiedName());
  }
}
