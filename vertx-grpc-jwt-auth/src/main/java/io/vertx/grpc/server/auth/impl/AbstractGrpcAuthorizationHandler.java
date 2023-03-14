package io.vertx.grpc.server.auth.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.grpc.common.GrpcException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.auth.GrpcAuthenticationHandler;

public abstract class AbstractGrpcAuthorizationHandler<T extends AuthenticationProvider> implements GrpcAuthenticationHandler {

  static final GrpcException UNAUTHENTICATED = new GrpcException(GrpcStatus.UNAUTHENTICATED);
  static final GrpcException UNKNOWN = new GrpcException(GrpcStatus.UNKNOWN);
  static final GrpcException UNIMPLEMENTED = new GrpcException(GrpcStatus.UNIMPLEMENTED);

  // this should match the IANA registry: https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml
  public enum Type {
    BASIC("Basic"),

    DIGEST("Digest"),

    BEARER("Bearer"),
    // these have no known implementation
    HOBA("HOBA"),

    MUTUAL("Mutual"),

    NEGOTIATE("Negotiate"),

    OAUTH("OAuth"),

    SCRAM_SHA_1("SCRAM-SHA-1"),

    SCRAM_SHA_256("SCRAM-SHA-256");

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

  protected final T authProvider;
  protected final Type type;
  protected final String realm;

  public AbstractGrpcAuthorizationHandler(T authProvider, Type type, String realm) {
    this.authProvider = authProvider;
    this.type = type;
    this.realm = realm == null ? null
      : realm
        // escape quotes
        .replaceAll("\"", "\\\"");

    if (this.realm != null &&
      (this.realm.indexOf('\r') != -1 || this.realm.indexOf('\n') != -1)) {
      throw new IllegalArgumentException("Not allowed [\\r|\\n] characters detected on realm name");
    }
  }

  protected final void parseAuthorization(GrpcServerRequest req, Handler<AsyncResult<String>> handler) {
    parseAuthorization(req, false, handler);
  }

  protected final void parseAuthorization(GrpcServerRequest req, boolean optional, Handler<AsyncResult<String>> handler) {

    final String authorization = req.headers().get(HttpHeaders.AUTHORIZATION);

    if (authorization == null) {
      if (optional) {
        // this is allowed
        handler.handle(Future.succeededFuture());
      } else {
        handler.handle(Future.failedFuture(UNAUTHENTICATED));
      }
      return;
    }

    try {
      int idx = authorization.indexOf(' ');

      if (idx <= 0) {
        handler.handle(Future.failedFuture(UNKNOWN));
        return;
      }

      if (!type.is(authorization.substring(0, idx))) {
        handler.handle(Future.failedFuture(UNAUTHENTICATED));
        return;
      }

      handler.handle(Future.succeededFuture(authorization.substring(idx + 1)));
    } catch (RuntimeException e) {
      handler.handle(Future.failedFuture(e));
    }
  }

}
