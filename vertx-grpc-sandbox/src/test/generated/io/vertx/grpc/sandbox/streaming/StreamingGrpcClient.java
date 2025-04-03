package io.vertx.grpc.sandbox.streaming;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.core.net.SocketAddress;
public abstract class StreamingGrpcClient implements StreamingClient {
  private final GrpcClient client;
  private final SocketAddress socketAddress;
  public StreamingGrpcClient(GrpcClient client, SocketAddress socketAddress) {
    this.client = client;
    this.socketAddress = socketAddress;
  }
  public io.vertx.core.Future<io.vertx.grpc.sandbox.streaming.Item> unary(io.vertx.grpc.sandbox.streaming.Item request) {
    ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Item> serviceMethod = ServiceMethod.client(
      ServiceName.create("streaming, Streaming"),
      "Unary",
      GrpcMessageEncoder.encoder(),
      GrpcMessageDecoder.decoder(io.vertx.grpc.sandbox.streaming.Item.parser())
      );
    return client.request(socketAddress, serviceMethod).compose(req -> {
      req.end(request);
      return req.response().compose(resp -> resp.last());
    });
  }
  public io.vertx.core.Future<io.vertx.core.streams.ReadStream<io.vertx.grpc.sandbox.streaming.Item>> source(io.vertx.grpc.sandbox.streaming.Empty request) {
    throw new UnsupportedOperationException();
  }
  public io.vertx.core.Future<io.vertx.grpc.sandbox.streaming.Empty> sink(io.vertx.core.streams.ReadStream<io.vertx.grpc.sandbox.streaming.Item> request) {
    throw new UnsupportedOperationException();
  }
  public io.vertx.core.Future<io.vertx.core.streams.ReadStream<io.vertx.grpc.sandbox.streaming.Item>> pipe(io.vertx.core.streams.ReadStream<io.vertx.grpc.sandbox.streaming.Item> request) {
    throw new UnsupportedOperationException();
  }
}
