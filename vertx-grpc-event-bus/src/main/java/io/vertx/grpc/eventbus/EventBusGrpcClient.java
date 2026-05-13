package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.ServiceInvoker;
import io.vertx.grpc.common.ServiceMethod;
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
 * <p>This implements {@link ServiceInvoker} so it can be used directly with generated gRPC client stubs
 * via the {@code create(GrpcClientService)} factory methods.</p>
 */
@VertxGen
public interface EventBusGrpcClient extends ServiceInvoker {

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

  /**
   * Create a request for the given service method.
   *
   * @param method the gRPC service method
   * @return a future request
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method);
}
