package io.vertx.grpc.server;

import io.vertx.core.Handler;
import io.vertx.grpc.common.GrpcMessageValidator;
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
   * Like {@link #bind(ServiceMethod, Handler)} but validating each request message with {@code validator}
   * before it is delivered to the {@code handler}.
   *
   * <p>The validator is invoked for every message of the call, whichever way the handler consumes the
   * request stream. A message that does not satisfy the rules fails the call with
   * {@link io.vertx.grpc.common.GrpcStatus#INVALID_ARGUMENT}.
   *
   * @param serviceMethod the service method
   * @param handler the service method call handler
   * @param validator the request message validator
   * @return a reference to this, so the API can be used fluently
   */
  <Req, Resp> ServiceBuilder bind(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler, GrpcMessageValidator<? super Req> validator);

  /**
   * Constructs and returns a {@link Service} instance based on the current configuration of the builder.
   *
   * @return a configured {@link Service} instance
   */
  Service build();

}
