package io.vertx.grpc.server.auth;

import io.grpc.MethodDescriptor;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.auth.impl.JWTGrpcServerImpl;

public interface JWTGrpcServer extends GrpcServer {

  static JWTGrpcServer create(GrpcServer server, JWTAuth authProvider) {
    return new JWTGrpcServerImpl(server, authProvider);
  }

  JWTGrpcServer callHandler(boolean requireAuthentication, Handler<GrpcServerRequest<Buffer, Buffer>> handler);

  <Req, Resp> JWTGrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, boolean requireAuthentication,
    Handler<GrpcServerRequest<Req, Resp>> handler);

  static JWTGrpcServer create(Vertx vertx, JWTAuth authProvider) {
    return create(GrpcServer.server(vertx), authProvider);
  }

}
