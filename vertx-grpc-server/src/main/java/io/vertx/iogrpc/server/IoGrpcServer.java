package io.vertx.iogrpc.server;

import io.grpc.MethodDescriptor;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.impl.GrpcServerImpl;

@VertxGen
public interface IoGrpcServer extends GrpcServer {

  /**
   * Create a blank gRPC server with default options.
   *
   * @return the created server
   */
  static IoGrpcServer server(Vertx vertx) {
    return server(vertx, new GrpcServerOptions());
  }

  /**
   * Create a blank gRPC server with specified options.
   *
   * @param options the gRPC server options
   * @return the created server
   */
  static IoGrpcServer server(Vertx vertx, GrpcServerOptions options) {
    return new GrpcServerImpl(vertx, options);
  }

  @Override
  IoGrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler);

  /**
   * Set a service method call handler that handles any call made to the server for the {@link MethodDescriptor} service method.
   *
   * @param handler the service method call handler
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> IoGrpcServer callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler);

}
