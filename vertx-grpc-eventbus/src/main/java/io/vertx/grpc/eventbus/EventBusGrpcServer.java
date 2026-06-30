package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.impl.EventBusGrpcServerImpl;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.ServiceContainer;
import io.vertx.grpc.server.Service;

/**
 * A gRPC server that uses the Vert.x {@link EventBus} as transport instead of HTTP/2.
 *
 * <p>This is useful for local or clustered communication within a Vert.x application,
 * allowing gRPC service implementations to handle requests received over the event bus
 * without the overhead of HTTP/2 connections.</p>
 *
 * <p>The server registers event bus consumers using the service's fully qualified name
 * as the address, and routes to specific method handlers using the {@code action} header.</p>
 */
@VertxGen
public interface EventBusGrpcServer extends ServiceContainer, Closeable {

  /**
   * Create an event bus gRPC server using the event bus from the provided Vert.x instance. The returned future
   * completes once the server's private address is bound.
   *
   * @param vertx the vertx instance
   * @return a future of the created server
   */
  static Future<EventBusGrpcServer> server(Vertx vertx) {
    return EventBusGrpcServerImpl.create(vertx, vertx.eventBus(), new EventBusGrpcServerOptions());
  }

  /**
   * Create an event bus gRPC server using the event bus from the provided Vert.x instance and the given options. The
   * returned future completes once the server's private address is bound.
   *
   * @param vertx the vertx instance
   * @param options the server options
   * @return a future of the created server
   */
  static Future<EventBusGrpcServer> server(Vertx vertx, EventBusGrpcServerOptions options) {
    return EventBusGrpcServerImpl.create(vertx, vertx.eventBus(), options);
  }

  @Fluent
  <Req, Resp> EventBusGrpcServer callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler);

  /**
   * Create an event bus gRPC server using the provided event bus. The returned future completes once the server's
   * private address is bound.
   *
   * @param vertx the vertx instance
   * @param eventBus the event bus to use as transport
   * @return a future of the created server
   */
  static Future<EventBusGrpcServer> server(Vertx vertx, EventBus eventBus) {
    return EventBusGrpcServerImpl.create(vertx, eventBus, new EventBusGrpcServerOptions());
  }

  /**
   * Create an event bus gRPC server using the provided event bus and options. The returned future completes once the
   * server's private address is bound.
   *
   * @param vertx the vertx instance
   * @param eventBus the event bus to use as transport
   * @param options the server options
   * @return a future of the created server
   */
  static Future<EventBusGrpcServer> server(Vertx vertx, EventBus eventBus, EventBusGrpcServerOptions options) {
    return EventBusGrpcServerImpl.create(vertx, eventBus, options);
  }

  @Override
  @Fluent
  EventBusGrpcServer addService(Service service);

}
