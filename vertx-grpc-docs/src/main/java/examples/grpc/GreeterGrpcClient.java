package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Completable;
import io.vertx.core.Handler;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;

/**
 * <p>A client for invoking the Greeter gRPC service.</p>
 */
public interface GreeterGrpcClient extends GreeterClient {

  /**
   * SayHello protobuf RPC client service method.
   */
  ServiceMethod<examples.grpc.HelloReply, examples.grpc.HelloRequest> SayHello = ServiceMethod.client(
    ServiceName.create("examples.grpc", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.HelloReply.newBuilder()));

  /**
   * Create and return a Greeter gRPC service client. The assumed wire format is Protobuf.
   *
   * @param client the gRPC client
   * @param host   the host providing the service
   * @return the configured client
   */
  static GreeterGrpcClient create(GrpcClient client, SocketAddress host) {
    return new GreeterGrpcClientImpl(client, host);
  }

  /**
   * Create and return a Greeter gRPC service client.
   *
   * @param client     the gRPC client
   * @param host       the host providing the service
   * @param wireFormat the wire format
   * @return the configured client
   */
  static GreeterGrpcClient create(GrpcClient client, SocketAddress host, io.vertx.grpc.common.WireFormat wireFormat) {
    return new GreeterGrpcClientImpl(client, host, wireFormat);
  }
}

/**
 * The proxy implementation.
 */
class GreeterGrpcClientImpl implements GreeterGrpcClient {

  private final GrpcClient client;
  private final SocketAddress socketAddress;
  private final io.vertx.grpc.common.WireFormat wireFormat;

  GreeterGrpcClientImpl(GrpcClient client, SocketAddress socketAddress) {
    this(client, socketAddress, io.vertx.grpc.common.WireFormat.PROTOBUF);
  }

  GreeterGrpcClientImpl(GrpcClient client, SocketAddress socketAddress, io.vertx.grpc.common.WireFormat wireFormat) {
    this.client = java.util.Objects.requireNonNull(client);
    this.socketAddress = java.util.Objects.requireNonNull(socketAddress);
    this.wireFormat = java.util.Objects.requireNonNull(wireFormat);
  }

  public Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request) {
    return client.request(socketAddress, SayHello).compose(req -> {
      req.format(wireFormat);
      return req.end(request).compose(v -> req.response().compose(resp -> resp.last()));
    });
  }
}
