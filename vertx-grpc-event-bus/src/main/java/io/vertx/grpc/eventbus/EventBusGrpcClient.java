package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.eventbus.impl.EventBusGrpcClientImpl;

/**
 * A gRPC client that uses the Vert.x {@link EventBus} as transport instead of HTTP/2.
 *
 * <p>This is useful for local or clustered communication within a Vert.x application,
 * allowing gRPC service interactions without the overhead of HTTP/2 connections.</p>
 *
 * <p>The client sends gRPC messages over the event bus using the service name
 * as the event bus address and the method name as a header for routing.</p>
 *
 * <p>This extends {@link GrpcClient} so it can be used with generated gRPC client stubs.
 * The {@code Address} parameter in request methods is ignored since the event bus uses the service name for addressing.</p>
 */
@VertxGen
public interface EventBusGrpcClient extends GrpcClient {

  /**
   * Create an event bus gRPC client using the event bus from the provided Vert.x instance.
   *
   * @param vertx the vertx instance
   * @return the created client
   */
  static EventBusGrpcClient create(Vertx vertx) {
    return new EventBusGrpcClientImpl(vertx, vertx.eventBus());
  }

  /**
   * Create an event bus gRPC client using the provided event bus.
   *
   * @param vertx the vertx instance
   * @param eventBus the event bus to use as transport
   * @return the created client
   */
  static EventBusGrpcClient create(Vertx vertx, EventBus eventBus) {
    return new EventBusGrpcClientImpl(vertx, eventBus);
  }

}
