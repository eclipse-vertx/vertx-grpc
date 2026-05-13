package io.vertx.grpc.server;

import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

@FunctionalInterface
public interface ServiceMethodInvoker<Req, Resp> {

  ServiceMethodInvoker<?, ? > DEFAULT_INVOKER = (ServiceMethodInvoker<Object, Object>) request -> request
    .response()
    .status(GrpcStatus.UNIMPLEMENTED)
    .end();

  static <Req, Resp> ServiceMethodInvoker<Req, Resp> defaultInvoker(ServiceMethod<Req, Resp> method) {
    return (ServiceMethodInvoker<Req, Resp>)DEFAULT_INVOKER;
  }

  /**
   * Handle the method call.
   *
   * @param request the service request
   */
  void invoke(GrpcServerRequest<Req, Resp> request);
}
