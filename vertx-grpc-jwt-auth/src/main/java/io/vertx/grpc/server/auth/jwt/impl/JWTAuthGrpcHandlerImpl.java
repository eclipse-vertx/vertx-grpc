package io.vertx.grpc.server.auth.jwt.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.common.GrpcException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.auth.jwt.JWTAuthGrpcHandler;

public class JWTAuthGrpcHandlerImpl extends AbstractGrpcAuthorizationHandler<JWTAuth> implements JWTAuthGrpcHandler {

  public JWTAuthGrpcHandlerImpl(JWTAuth authProvider, String realm) {
    super(authProvider, Type.BEARER, realm);
  }

  @Override
  public <Req, Resp> void authenticate(GrpcServerRequest<Req, Resp> req, boolean requireAuthentication, Handler<AsyncResult<User>> handler) {
    parseAuthorization(req, !requireAuthentication, parseAuthorization -> {
      if (parseAuthorization.failed()) {
        handler.handle(Future.failedFuture(parseAuthorization.cause()));
        return;
      }

      String token = parseAuthorization.result();
      if (token == null) {
        handler.handle(Future.failedFuture(new GrpcException(GrpcStatus.UNKNOWN, "Missing token")));
        return;
      }
      int segments = 0;
      for (int i = 0; i < token.length(); i++) {
        char c = token.charAt(i);
        if (c == '.') {
          if (++segments == 3) {
            handler.handle(Future.failedFuture(new GrpcException(GrpcStatus.UNKNOWN, "Too many segments in token")));
            return;
          }
          continue;
        }
        if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
          continue;
        }
        // invalid character
        handler.handle(Future.failedFuture(new GrpcException(GrpcStatus.UNKNOWN, "Invalid character in token: " + (int) c)));
        return;
      }

      authProvider.authenticate(new TokenCredentials(token)).andThen(authn -> {
        if (authn.failed()) {
          handler.handle(Future.failedFuture(new GrpcException(GrpcStatus.UNAUTHENTICATED, authn.cause())));
        } else {
          handler.handle(authn);
        }
      });
    });
  }

}
