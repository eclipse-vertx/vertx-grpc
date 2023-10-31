package io.vertx.grpc.server.auth;

import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.server.auth.impl.GrpcJWTAuthenticationHandlerImpl;

/**
 * Authentication handler which provides JWT authentication.
 */
public interface GrpcJWTAuthenticationHandler extends GrpcAuthenticationHandler {

  static GrpcJWTAuthenticationHandler create(JWTAuth authProvider) {
    return create(authProvider, "");
  }

  static GrpcJWTAuthenticationHandler create(JWTAuth authProvider, String realm) {
    return new GrpcJWTAuthenticationHandlerImpl(authProvider, realm);
  }

}
