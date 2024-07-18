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
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.iogrpc.server.impl.IoGrpcServerImpl;

/**
 * A gRPC server based on Vert.x HTTP server.
 *
 * This server extends the {@link GrpcServer} to encode/decode messages in protobuf format using {@link io.grpc.MethodDescriptor}.
 *
 * This server exposes 2 levels of handlers
 *
 * <ul>
 *   <li>a Protobuf message {@link #callHandler(Handler) handler}: {@link GrpcServerRequest}/{@link GrpcServerResponse} with Protobuf message that handles any method call in a generic way</li>
 *   <li>a gRPC message {@link #callHandler(MethodDescriptor, Handler) handler}: {@link GrpcServerRequest}/{@link GrpcServerRequest} with gRPC messages that handles specific service method calls</li>
 * </ul>
 */
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
    return new IoGrpcServerImpl(vertx, options);
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
