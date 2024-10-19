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

public class VertxGreeterGrpcClient {

  public static final ServiceMethod<examples.HelloReply, examples.HelloRequest> SayHello = ServiceMethod.client(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.HelloReply.parser()));
  public static final ServiceMethod<examples.HelloReply, examples.HelloRequest> SayHello_JSON = ServiceMethod.client(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.HelloReply.newBuilder()));

  private final GrpcClient client;
  private final SocketAddress socketAddress;
  private final io.vertx.grpc.common.WireFormat wireFormat;

  public VertxGreeterGrpcClient(GrpcClient client, SocketAddress socketAddress) {
    this(client, socketAddress, io.vertx.grpc.common.WireFormat.PROTOBUF);
  }

  public VertxGreeterGrpcClient(GrpcClient client, SocketAddress socketAddress, io.vertx.grpc.common.WireFormat wireFormat) {
    this.client = java.util.Objects.requireNonNull(client);
    this.socketAddress = java.util.Objects.requireNonNull(socketAddress);
    this.wireFormat = java.util.Objects.requireNonNull(wireFormat);
  }

  public Future<examples.HelloReply> sayHello(examples.HelloRequest request) {
    ServiceMethod<examples.HelloReply,examples.HelloRequest> serviceMethod;
    switch(wireFormat) {
      case PROTOBUF:
        serviceMethod = SayHello;
        break;
      case JSON:
        serviceMethod = SayHello_JSON;
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
