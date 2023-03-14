package io.vertx.grpc.server.auth;

import io.grpc.MethodDescriptor;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.auth.impl.JWTGrpcServerImpl;

/**
 * A gRPC server which is able to use JWT based authentication.
 * @see GrpcServer for more information on general gRPC server usage. 
 */
public interface JWTGrpcServer extends GrpcServer {

  /**
   * Create a new server.
   * 
   * @param server gRPC server to which calls will be delegated
   * @param authProvider authentication provider to be used to authenticate JWT
   * @return the created server
   */
  static JWTGrpcServer create(GrpcServer server, JWTAuth authProvider) {
    return new JWTGrpcServerImpl(server, authProvider);
  }

  /**
   * Create a new server.
   * 
   * @param vertx the vertx instance
   * @param authProvider
   * @return the created server
   */
  static JWTGrpcServer create(Vertx vertx, JWTAuth authProvider) {
    return create(GrpcServer.server(vertx), authProvider);
  }

  /**
   * Set a call handler that handles any call made to the server.
   * 
   * @param requireAuthentication flag to indicate whether valid authentication is required to access the call handler
   * @param handler handler the service method call handler
   * @return Fluent API
   */
  JWTGrpcServer callHandler(boolean requireAuthentication, Handler<GrpcServerRequest<Buffer, Buffer>> handler);

  /**
   * Set a service method call handler that handles any call call made to the server for the {@link MethodDescriptor} service method.
   * 
   * @param methodDesc method for which a handler will be provided
   * @param requireAuthentication flag to indicate whether valid authentication is required to access the call handler
   * @param handler the service method call handler
   * @return Fluent API
   */
  <Req, Resp> JWTGrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, boolean requireAuthentication,
    Handler<GrpcServerRequest<Req, Resp>> handler);


}
