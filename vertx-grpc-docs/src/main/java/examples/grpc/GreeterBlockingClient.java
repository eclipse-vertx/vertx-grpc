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
 * <p>A client for invoking the Greeter gRPC service.</p>
 */
public interface GreeterBlockingClient {

  static GreeterBlockingClient create(GreeterClient client) {
    return new GreeterBlockingClientImpl(client);
  }

}

/**
 * The proxy implementation.
 */
class GreeterBlockingClientImpl implements GreeterBlockingClient {

  private final GreeterClientInternal client;

  GreeterBlockingClientImpl(Greeter client) {
    this.client = (GreeterClientInternal)java.util.Objects.requireNonNull(client);
  }
}
