package examples;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.GrpcWriteStream;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;

/**
 * <p>A client for invoking the Greeter gRPC service.</p>
 */
@io.vertx.codegen.annotations.VertxGen
public interface GreeterClient {

  /**
   * SayHello protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<examples.HelloReply, examples.HelloRequest> SayHello = ServiceMethod.client(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.HelloReply.parser()));

  /**
   * Json client service methods.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  final class Json {

    /**
     * SayHello json RPC client service method.
     */
    public static final ServiceMethod<examples.HelloReply, examples.HelloRequest> SayHello = ServiceMethod.client(
      ServiceName.create("helloworld", "Greeter"),
      "SayHello",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.HelloReply.newBuilder()));
  }

  /**
   * Create and return a Greeter gRPC service client. The assumed wire format is Protobuf.
   *
   * @param client the gRPC client
   * @param host   the host providing the service
   * @return the configured client
   */
  static GreeterClient create(GrpcClient client, SocketAddress host) {
    return new GreeterClientImpl(client, host);
  }

  /**
   * Create and return a Greeter gRPC service client.
   *
   * @param client     the gRPC client
   * @param host       the host providing the service
   * @param wireFormat the wire format
   * @return the configured client
   */
  static GreeterClient create(GrpcClient client, SocketAddress host, io.vertx.grpc.common.WireFormat wireFormat) {
    return new GreeterClientImpl(client, host, wireFormat);
  }

  /**
   * Calls the SayHello RPC service method.
   *
   * @param request the examples.HelloRequest request message
   * @return a future of the examples.HelloReply response message
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<examples.HelloReply> sayHello(examples.HelloRequest request);
}

/**
 * The proxy implementation.
 */
class GreeterClientImpl implements GreeterClient {

  private final GrpcClient client;
  private final SocketAddress socketAddress;
  private final io.vertx.grpc.common.WireFormat wireFormat;

  GreeterClientImpl(GrpcClient client, SocketAddress socketAddress) {
    this(client, socketAddress, io.vertx.grpc.common.WireFormat.PROTOBUF);
  }

  GreeterClientImpl(GrpcClient client, SocketAddress socketAddress, io.vertx.grpc.common.WireFormat wireFormat) {
    this.client = java.util.Objects.requireNonNull(client);
    this.socketAddress = java.util.Objects.requireNonNull(socketAddress);
    this.wireFormat = java.util.Objects.requireNonNull(wireFormat);
  }

  public Future<examples.HelloReply> sayHello(examples.HelloRequest request) {
    ServiceMethod<examples.HelloReply, examples.HelloRequest> serviceMethod;
    switch (wireFormat) {
      case PROTOBUF:
        serviceMethod = SayHello;
        break;
      case JSON:
        serviceMethod = Json.SayHello;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod).compose(req -> {
      req.end(request);
      return req.response().compose(resp -> resp.last());
    });
  }

}
