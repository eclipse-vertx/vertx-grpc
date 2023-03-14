package io.vertx.grpc.server.auth;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.grpc.server.GrpcServerRequest;

//@VertxGen(concrete = false)
public interface GrpcAuthenticationHandler {

  void authenticate(GrpcServerRequest req, Handler<AsyncResult<User>> handler);

}
