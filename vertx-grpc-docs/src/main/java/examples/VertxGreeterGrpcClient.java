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
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;

public class VertxGreeterGrpcClient {

  public static final ServiceMethod<examples.HelloReply, examples.HelloRequest> SayHello = ServiceMethod.client(
  ServiceName.create("helloworld", "Greeter"),
  "SayHello",
  GrpcMessageEncoder.encoder(),
  GrpcMessageDecoder.decoder(examples.HelloReply.parser()));

  private final GrpcClient client;
  private final SocketAddress socketAddress;

  public VertxGreeterGrpcClient(GrpcClient client, SocketAddress socketAddress) {
    this.client = client;
    this.socketAddress = socketAddress;
  }

  public Future<examples.HelloReply> sayHello(examples.HelloRequest request) {
    return client.request(socketAddress, SayHello).compose(req -> {
      req.end(request);
      return req.response().compose(resp -> resp.last());
    });
  }

}
