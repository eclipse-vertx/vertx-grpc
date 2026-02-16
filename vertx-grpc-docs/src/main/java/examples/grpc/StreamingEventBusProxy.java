package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.streams.ReadStream;

/**
 * <p>Event bus proxy for the Streaming gRPC service.</p>
 *
 * <p>Implements the {@link Streaming} contract interface by sending protobuf-serialized
 * messages over the Vert.x event bus. Only unary-unary methods are supported.</p>
 *
 * <p>Address format: {@code examples.grpc.Streaming/MethodName}</p>
 */
public class StreamingEventBusProxy implements Streaming {

  private final Vertx vertx;
  private final DeliveryOptions options;

  public StreamingEventBusProxy(Vertx vertx) {
    this(vertx, null);
  }

  public StreamingEventBusProxy(Vertx vertx, DeliveryOptions options) {
    this.vertx = vertx;
    this.options = options;
  }

  @Override
  public Future<ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request) {
    throw new UnsupportedOperationException("Streaming methods are not supported over the event bus");
  }

  @Override
  public Future<examples.grpc.Empty> sink(ReadStream<examples.grpc.Item> request) {
    throw new UnsupportedOperationException("Streaming methods are not supported over the event bus");
  }

  @Override
  public Future<ReadStream<examples.grpc.Item>> pipe(ReadStream<examples.grpc.Item> request) {
    throw new UnsupportedOperationException("Streaming methods are not supported over the event bus");
  }
}
