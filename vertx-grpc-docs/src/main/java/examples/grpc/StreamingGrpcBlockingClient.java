package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Completable;
import io.vertx.core.Handler;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import java.util.stream.Stream;

/**
 * <p>A client for invoking the Streaming gRPC service.</p>
 */
public interface StreamingGrpcBlockingClient {

  static StreamingGrpcBlockingClient create(StreamingGrpcClient client) {
    return new StreamingGrpcBlockingClientImpl(client);
  }


  @io.vertx.codegen.annotations.GenIgnore
  Stream<examples.grpc.Item> source(examples.grpc.Empty request);

  @io.vertx.codegen.annotations.GenIgnore
  default examples.grpc.Empty sink(java.util.List<examples.grpc.Item> streamOfMessages) {
    return sink(streamOfMessages.iterator());
  }

  @io.vertx.codegen.annotations.GenIgnore
  examples.grpc.Empty sink(java.util.Iterator<examples.grpc.Item> streamOfMessages);
}

/**
 * The proxy implementation.
 */
class StreamingGrpcBlockingClientImpl implements StreamingGrpcBlockingClient {

  private final StreamingGrpcClientImpl client;

  StreamingGrpcBlockingClientImpl(Streaming client) {
    this.client = (StreamingGrpcClientImpl)java.util.Objects.requireNonNull(client);
  }

  public Stream<examples.grpc.Item> source(examples.grpc.Empty request) {
    Stream<examples.grpc.Item> iterator = client.source_(request)
      .compose(req -> {
        req.end(request);
        return req.response().compose(resp -> {
          if (resp.status() != null && resp.status() != GrpcStatus.OK) {
            return Future.failedFuture("Invalid gRPC status " + resp.status());
          } else {
            return Future.succeededFuture(resp.blockingStream());
          }
        });
      }).await();
    return iterator;
  }

  public examples.grpc.Empty sink(java.util.Iterator<examples.grpc.Item> request) {
    throw new UnsupportedOperationException();
  }
}
