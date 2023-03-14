package io.vertx.grpc.server.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.auth.impl.JWTAuthGrpcHandlerImpl;

public interface JWTAuthGrpcHandler extends GrpcAuthenticationHandler {

  static JWTAuthGrpcHandler create(JWTAuth authProvider) {
    return create(authProvider, null);
  }

  static JWTAuthGrpcHandler create(JWTAuth authProvider, String string) {
    return new JWTAuthGrpcHandlerImpl(authProvider, null);
  }
}
