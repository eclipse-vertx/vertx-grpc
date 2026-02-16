package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.streams.ReadStream;

/**
 * <p>Event bus proxy for the Greeter gRPC service.</p>
 *
 * <p>Implements the {@link Greeter} contract interface by sending protobuf-serialized
 * messages over the Vert.x event bus. Only unary-unary methods are supported.</p>
 *
 * <p>Address format: {@code examples.grpc.Greeter/MethodName}</p>
 */
public class GreeterEventBusProxy implements Greeter {

  private final Vertx vertx;
  private final DeliveryOptions options;

  public GreeterEventBusProxy(Vertx vertx) {
    this(vertx, null);
  }

  public GreeterEventBusProxy(Vertx vertx, DeliveryOptions options) {
    this.vertx = vertx;
    this.options = options;
  }

  @Override
  public Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request) {
    DeliveryOptions deliveryOptions = (options != null) ? new DeliveryOptions(options) : new DeliveryOptions();
    return vertx.eventBus().<Buffer>request("examples.grpc.Greeter/SayHello", Buffer.buffer(request.toByteArray()), deliveryOptions)
      .map(msg -> {
        try {
          return examples.grpc.HelloReply.parseFrom(msg.body().getBytes());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          throw new RuntimeException(e);
        }
      });
  }
}
