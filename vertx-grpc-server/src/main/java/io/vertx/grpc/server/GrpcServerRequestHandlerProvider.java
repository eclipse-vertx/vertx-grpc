package io.vertx.grpc.server;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.Handler;
import io.vertx.grpc.common.GrpcMessageValidator;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

@Unstable
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface GrpcServerRequestHandlerProvider {

  GrpcServerRequestHandlerProvider DEFAULT_INSTANCE = new GrpcServerRequestHandlerProvider() {
  };

  /**
   * Obtain a service method invoker.
   *
   * @param method the service method
   */
  default <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
    return request -> request
      .response()
      .status(GrpcStatus.UNIMPLEMENTED)
      .end();
  }

  /**
   * Obtain the validator applied to the request messages of a service method.
   *
   * @param method the service method
   * @return the validator, or {@code null} when the method is not validated
   */
  default <Req, Resp> GrpcMessageValidator<? super Req> validator(ServiceMethod<Req, Resp> method) {
    return null;
  }
}
