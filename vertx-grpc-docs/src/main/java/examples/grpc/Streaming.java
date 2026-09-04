package examples.grpc;

/**
 * <p>Contract definition Streaming service.</p>
 */
public interface Streaming {

  io.vertx.core.Future<io.vertx.core.streams.ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request);

  io.vertx.core.Future<examples.grpc.Empty> sink(io.vertx.core.streams.ReadStream<examples.grpc.Item> request);

  io.vertx.core.Future<io.vertx.core.streams.ReadStream<examples.grpc.Item>> pipe(io.vertx.core.streams.ReadStream<examples.grpc.Item> request);

}
