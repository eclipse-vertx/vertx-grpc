package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.streams.ReadStream;



/**
 * <p>Contract definition Streaming service.</p>
 */
public interface Streaming {

  Future<ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request);

  Future<examples.grpc.Empty> sink(ReadStream<examples.grpc.Item> request);

  Future<ReadStream<examples.grpc.Item>> pipe(ReadStream<examples.grpc.Item> request);

}
