package io.vertx.grpc.server;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.grpc.common.ServiceMethod;

@Unstable
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface ServiceInvoker {

  ServiceInvoker DEFAULT_INSTANCE = new ServiceInvoker() {
  };

  /**
   * Obtain a service method invoker.
   *
   * @param method the service method
   */
  default <Req, Resp> ServiceMethodInvoker<Req, Resp> invoker(ServiceMethod<Req, Resp> method) {
    return ServiceMethodInvoker.defaultInvoker(method);
  }
}
