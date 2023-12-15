package io.vertx.grpc.server.auth;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.User;
import io.vertx.grpc.server.GrpcServer;

/**
 * Authentication handler for {@link GrpcServer}.
 */
@VertxGen
@FunctionalInterface
public interface GrpcAuthenticationHandler {

  /**
   * Authenticate the provided request and return the authenticated user.
   * 
   * @param httpRequest Request to authenticate
   * @param requireAuthentication Whether the handler should fail when no authentication is present
   * @return
   */
  Future<User> authenticate(HttpServerRequest httpRequest, boolean requireAuthentication);

}
