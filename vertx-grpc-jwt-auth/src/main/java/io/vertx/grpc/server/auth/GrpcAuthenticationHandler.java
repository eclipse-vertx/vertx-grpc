package io.vertx.grpc.server.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.grpc.server.GrpcServerRequest;

/**
 * A gRPC authentication handler which can provide a {@link User} by inspecting the {@link GrpcServerRequest}.
 */
public interface GrpcAuthenticationHandler {

  /**
   * Authenticate the provided request and invoke the handler with the result of the authentication process.
   * 
   * When authentication is not required it will succeed the handler without providing a user. Otherwise the {@link AsyncResult} will fail.
   * 
   * @param req the inbound server request
   * @param requireAuthentication flag which indicates whether authentication must be present for the authentication process to not fail
   * @param handler the handler which provides the result of the authentication
   */
  <Req, Resp> void authenticate(GrpcServerRequest<Req, Resp> req, boolean requireAuthentication, Handler<AsyncResult<User>> handler);

}
