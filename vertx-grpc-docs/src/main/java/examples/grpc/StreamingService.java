package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Completable;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;



/**
 * <p>Provides support for RPC methods implementations of the Streaming gRPC service.</p>
 *
 * <p>The following methods of this class should be overridden to provide an implementation of the service:</p>
 * <ul>
 *   <li>Source</li>
 *   <li>Sink</li>
 *   <li>Pipe</li>
 * </ul>
 */
public class StreamingService implements Streaming {

  /**
   * Override this method to implement the Source RPC.
   */
  public Future<ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void source(examples.grpc.Empty request, WriteStream<examples.grpc.Item> response) {
    source(request)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          ReadStream<examples.grpc.Item> stream = ar.result();
          stream.pipeTo(response);
        } else {
          // Todo
        }
      });
  }

  /**
   * Override this method to implement the Sink RPC.
   */
  public Future<examples.grpc.Empty> sink(ReadStream<examples.grpc.Item> request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void sink(ReadStream<examples.grpc.Item> request, Completable<examples.grpc.Empty> response) {
    sink(request).onComplete(response);
  }

  /**
   * Override this method to implement the Pipe RPC.
   */
  public Future<ReadStream<examples.grpc.Item>> pipe(ReadStream<examples.grpc.Item> request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void pipe(ReadStream<examples.grpc.Item> request, WriteStream<examples.grpc.Item> response) {
    pipe(request)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          ReadStream<examples.grpc.Item> stream = ar.result();
          stream.pipeTo(response);
        } else {
          // Todo
        }
      });
  }
}
