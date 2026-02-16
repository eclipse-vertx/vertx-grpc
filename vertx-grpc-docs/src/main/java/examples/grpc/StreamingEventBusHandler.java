package examples.grpc;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.EventBus;

/**
 * <p>Event bus handler for the Streaming gRPC service.</p>
 *
 * <p>Registers event bus consumers for each unary-unary method, delegating to the
 * {@link Streaming} contract interface.</p>
 *
 * <p>Address format: {@code examples.grpc.Streaming/MethodName}</p>
 */
public class StreamingEventBusHandler {

  private final Streaming service;

  public StreamingEventBusHandler(Streaming service) {
    this.service = service;
  }

  /**
   * Registers event bus consumers for all unary-unary methods of this service.
   *
   * @param eventBus the Vert.x event bus to register consumers on
   */
  public void register(EventBus eventBus) {
  }
}
