package io.vertx.grpc.server;

import io.vertx.core.Handler;
import io.vertx.grpc.common.ServiceMethod;

/**
 * A builder for creating and configuring a {@link Service}. This interface allows you to bind
 * service methods to their respective handlers, which define how requests to those methods
 * are processed.
 */
public interface ServiceBuilder {

  /**
   * Bind a service method call handler that handles any call made to the server for the {@code fullMethodName} method.
   *
   * @param handler the service method call handler
   * @param serviceMethod the service method
   * @return a reference to this, so the API can be used fluently
   */
  <Req, Resp> ServiceBuilder bind(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler);

  /**
   * Constructs and returns a {@link Service} instance based on the current configuration of the builder.
   *
   * @return a configured {@link Service} instance
   */
  Service build();

}
