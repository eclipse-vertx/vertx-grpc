package examples.grpc;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.EventBus;

/**
 * <p>Event bus handler for the Greeter gRPC service.</p>
 *
 * <p>Registers event bus consumers for each unary-unary method, delegating to the
 * {@link Greeter} contract interface.</p>
 *
 * <p>Address format: {@code examples.grpc.Greeter/MethodName}</p>
 */
public class GreeterEventBusHandler {

  private final Greeter service;

  public GreeterEventBusHandler(Greeter service) {
    this.service = service;
  }

  /**
   * Registers event bus consumers for all unary-unary methods of this service.
   *
   * @param eventBus the Vert.x event bus to register consumers on
   */
  public void register(EventBus eventBus) {
    eventBus.consumer("examples.grpc.Greeter/SayHello", this::handleSayHello);
  }

  private void handleSayHello(Message<Buffer> message) {
    try {
      examples.grpc.HelloRequest request = examples.grpc.HelloRequest.parseFrom(message.body().getBytes());
      service.sayHello(request).onComplete(ar -> {
        if (ar.succeeded()) {
          message.reply(Buffer.buffer(ar.result().toByteArray()));
        } else {
          message.fail(500, ar.cause().getMessage());
        }
      });
    } catch (Exception e) {
      message.fail(500, e.getMessage());
    }
  }
}
