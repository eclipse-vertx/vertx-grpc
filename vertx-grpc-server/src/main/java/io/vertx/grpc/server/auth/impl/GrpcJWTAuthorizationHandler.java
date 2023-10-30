package io.vertx.grpc.server.auth.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcException;

public class GrpcJWTAuthorizationHandler {

  static final GrpcException UNAUTHENTICATED = new GrpcException(
    GrpcStatus.UNAUTHENTICATED);
  static final GrpcException UNKNOWN = new GrpcException(GrpcStatus.UNKNOWN);
  static final GrpcException UNIMPLEMENTED = new GrpcException(
    GrpcStatus.UNIMPLEMENTED);

  public enum Type {
    BASIC("Basic"), DIGEST("Digest"), BEARER("Bearer"),
    // these have no known implementation
    HOBA("HOBA"), MUTUAL("Mutual"), NEGOTIATE("Negotiate"), OAUTH(
      "OAuth"), SCRAM_SHA_1("SCRAM-SHA-1"), SCRAM_SHA_256("SCRAM-SHA-256");

    private final String label;

    Type(String label) {
      this.label = label;
    }

    public boolean is(String other) {
      return label.equalsIgnoreCase(other);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private final AuthenticationProvider authProvider;
  private final Type type;
  private final String realm;

  public GrpcJWTAuthorizationHandler(JWTAuth authProvider, String realm) {
    this(authProvider, Type.BEARER, realm);
  }

  public GrpcJWTAuthorizationHandler(AuthenticationProvider authProvider,
    Type type, String realm) {
    this.authProvider = authProvider;
    this.type = type;
    this.realm = realm == null ? null
      : realm
        // escape quotes
        .replaceAll("\"", "\\\"");

    if (this.realm != null &&
      (this.realm.indexOf('\r') != -1 || this.realm.indexOf('\n') != -1)) {
      throw new IllegalArgumentException(
        "Not allowed [\\r|\\n] characters detected on realm name");
    }
  }

  private Future<String> parseAuthorization(HttpServerRequest req,
    boolean optional) {

    final String authorization = req.headers().get(HttpHeaders.AUTHORIZATION);

    if (authorization == null) {
      if (optional) {
        // this is allowed
        return Future.succeededFuture();
      } else {
        return Future.failedFuture(UNAUTHENTICATED);
      }
    }

    try {
      int idx = authorization.indexOf(' ');

      if (idx <= 0) {
        return Future.failedFuture(UNKNOWN);
      }

      if (!type.is(authorization.substring(0, idx))) {
        return Future.failedFuture(UNAUTHENTICATED);
      }

      return Future.succeededFuture(authorization.substring(idx + 1));
    } catch (RuntimeException e) {
      return Future.failedFuture(e);
    }
  }

  protected Future<String> parseAuthorization(HttpServerRequest req) {
    return parseAuthorization(req, false);
  }

  public Future<User> authenticate(HttpServerRequest req,
    boolean requireAuthentication) {
    return parseAuthorization(req, !requireAuthentication)
      .compose(token -> {

        if (token == null) {
          return Future.failedFuture(
            new GrpcException(GrpcStatus.UNKNOWN, "Missing token"));
        }
        int segments = 0;
        for (int i = 0; i < token.length(); i++) {
          char c = token.charAt(i);
          if (c == '.') {
            if (++segments == 3) {
              return Future.failedFuture(new GrpcException(GrpcStatus.UNKNOWN,
                "Too many segments in token"));
            }
            continue;
          }
          if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
            continue;
          }
          // invalid character
          return Future.failedFuture(new GrpcException(GrpcStatus.UNKNOWN,
            "Invalid character in token: " + (int) c));
        }

        return authProvider.authenticate(new TokenCredentials(token))
          .recover(error -> {
            return Future.failedFuture(
              new GrpcException(GrpcStatus.UNAUTHENTICATED, error));
          });
      });
  }

}
