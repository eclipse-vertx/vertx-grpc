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
public interface GreeterGrpcBlockingClient {

  static GreeterGrpcBlockingClient create(GreeterGrpcClient client) {
    return new GreeterGrpcBlockingClientImpl(client);
  }


  @io.vertx.codegen.annotations.GenIgnore
  examples.grpc.HelloReply sayHello(examples.grpc.HelloRequest request);
}

/**
 * The proxy implementation.
 */
class GreeterGrpcBlockingClientImpl implements GreeterGrpcBlockingClient {

  private final GreeterGrpcClientImpl client;

  GreeterGrpcBlockingClientImpl(Greeter client) {
    this.client = (GreeterGrpcClientImpl)java.util.Objects.requireNonNull(client);
  }

  public examples.grpc.HelloReply sayHello(examples.grpc.HelloRequest request) {
    return client.sayHello(request).await();
  }
}
