package io.vertx.grpc.health;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.health.impl.HealthServiceImpl;
import io.vertx.grpc.server.Service;

import java.util.function.Supplier;

/**
 * A service that implements the standard gRPC health checking protocol.
 * <p>
 * This service provides a health checking system for gRPC services. It allows services to register health checks and have their
 * health status queried via gRPC.
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
public interface HealthService extends Service {

  /**
   * Creates a new HealthService instance with default options.
   *
   * @param vertx the Vert.x instance
   * @return a new HealthService instance
   */
  static HealthService create(Vertx vertx) {
    return create(vertx, new HealthServiceOptions());
  }

  /**
   * Creates a new HealthService instance with the specified options.
   *
   * @param vertx the Vert.x instance
   * @param options the options for configuring the health service
   * @return a new HealthService instance
   */
  static HealthService create(Vertx vertx, HealthServiceOptions options) {
    return new HealthServiceImpl(vertx, options);
  }

  /**
   * Registers a health check procedure for the specified service.
   *
   * @param name the service name to register the health check for
   * @param handler the handler that will be called to check the health status
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default HealthService register(ServiceName name, Supplier<Future<Boolean>> handler) {
    return register(name.fullyQualifiedName(), handler);
  }

  /**
   * Registers a health check procedure for the specified service.
   *
   * @param name the service name to register the health check for
   * @param handler the handler that will be called to check the health status
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HealthService register(String name, Supplier<Future<Boolean>> handler);

  /**
   * Unregisters a health check procedure for the specified service.
   *
   * @param name the service name to unregister the health check for
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default HealthService unregister(ServiceName name) {
    return unregister(name.fullyQualifiedName());
  }

  /**
   * Unregisters a health check procedure for the specified service.
   *
   * @param name the service name to unregister the health check for
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HealthService unregister(String name);

  /**
   * Checks the health status of the specified service.
   *
   * @param name the service name to check the health status for
   * @return a future that will be completed with the health check result
   */
  default Future<Boolean> checkStatus(ServiceName name) {
    return checkStatus(name.fullyQualifiedName());
  }

  /**
   * Checks the health status of the specified service.
   *
   * @param name the service name to check the health status for
   * @return a future that will be completed with the health check result
   */
  Future<Boolean> checkStatus(String name);
}
