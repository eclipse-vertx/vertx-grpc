package io.vertx.grpc.client;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

/**
 * A contract for issuing gRPC client requests by service method, without requiring
 * a specific transport or server address.
 *
 * <p>Generated gRPC client stubs use this interface as their underlying transport abstraction,
 * allowing them to work with any implementation - HTTP/2 via {@link GrpcClient}, event bus, etc.</p>
 */
@VertxGen
public interface GrpcClientService {

  /**
   * Create a request for the given service method.
   *
   * @param method the gRPC service method
   * @return a future request
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method);

}
