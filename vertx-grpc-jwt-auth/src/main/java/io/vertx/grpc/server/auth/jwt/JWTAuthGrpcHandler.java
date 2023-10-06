package io.vertx.grpc.server.auth.jwt;

import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.server.auth.jwt.impl.JWTAuthGrpcHandlerImpl;

/**
 * A gRPC authentication handler which JWT authentication support. 
 */
public interface JWTAuthGrpcHandler extends GrpcAuthenticationHandler {

  /**
   * Create a new handler
   * 
   * @param authProvider provider for jwt decoding / validation
   * @return the created handler
   */
  static JWTAuthGrpcHandler create(JWTAuth authProvider) {
    return create(authProvider, null);
  }

  /**
   * Create a new handler
   * 
   * @param authProvider provider for jwt decoding / validation
   * @param realm
   * @return the created handler
   */
  static JWTAuthGrpcHandler create(JWTAuth authProvider, String realm) {
    return new JWTAuthGrpcHandlerImpl(authProvider, realm);
  }

}
