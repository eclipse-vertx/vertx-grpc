package io.vertx.grpc.server.auth.jwt.impl;

import io.grpc.MethodDescriptor;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.common.GrpcException;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.auth.jwt.JWTAuthGrpcHandler;
import io.vertx.grpc.server.auth.jwt.JWTGrpcServer;

public class JWTGrpcServerImpl implements JWTGrpcServer {

  private final GrpcServer delegate;
  private final JWTAuthGrpcHandler authHandler;

  public JWTGrpcServerImpl(GrpcServer delegate, JWTAuth authProvider) {
    this.delegate = delegate;
    this.authHandler = JWTAuthGrpcHandler.create(authProvider);
  }

  @Override
  public JWTGrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    delegate.callHandler(handler);
    return this;
  }

  @Override
  public JWTGrpcServer callHandler(boolean requireAuthentication, Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    delegate.callHandler(handler);
    return this;
  }

  @Override
  public <Req, Resp> JWTGrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, boolean requireAuthentication,
    Handler<GrpcServerRequest<Req, Resp>> handler) {
    delegate.callHandler(methodDesc, req -> {
      User user = req.user();
      if (user != null) {
        handler.handle(req);
        return;
      }
      authHandler.authenticate(req, requireAuthentication, authN -> {
        if (authN.succeeded()) {
          User authenticated = authN.result();
          req.setUser(authenticated);
          handler.handle(req);
        } else {
          // to allow further processing if needed
          Throwable cause = authN.cause();
          if (cause instanceof GrpcException) {
            GrpcException grpcCause = (GrpcException)cause;
            req.response().status(grpcCause.status());
          }
          req.response().end();
        }
      });
    });
    return this;
  }

  @Override
  public <Req, Resp> JWTGrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler) {
    delegate.callHandler(methodDesc, handler);
    return this;
  }

  @Override
  public void handle(HttpServerRequest event) {
    delegate.handle(event);
  }

}
