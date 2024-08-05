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

public class VertxStreamingGrpcClient {

  public static final ServiceMethod<examples.Item, examples.Empty> Source = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));
  public static final ServiceMethod<examples.Item, examples.Empty> Source_JSON = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Source",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));
  public static final ServiceMethod<examples.Empty, examples.Item> Sink = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Empty.parser()));
  public static final ServiceMethod<examples.Empty, examples.Item> Sink_JSON = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Sink",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.Empty.newBuilder()));
  public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));
  public static final ServiceMethod<examples.Item, examples.Item> Pipe_JSON = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));

  private final GrpcClient client;
  private final SocketAddress socketAddress;
  private final io.vertx.grpc.common.WireFormat wireFormat;

  public VertxStreamingGrpcClient(GrpcClient client, SocketAddress socketAddress) {
    this(client, socketAddress, io.vertx.grpc.common.WireFormat.PROTOBUF);
  }

  public VertxStreamingGrpcClient(GrpcClient client, SocketAddress socketAddress, io.vertx.grpc.common.WireFormat wireFormat) {
    this.client = java.util.Objects.requireNonNull(client);
    this.socketAddress = java.util.Objects.requireNonNull(socketAddress);
    this.wireFormat = java.util.Objects.requireNonNull(wireFormat);
  }

  public Future<ReadStream<examples.Item>> source(examples.Empty request) {
    ServiceMethod<examples.Item,examples.Empty> serviceMethod;
    switch(wireFormat) {
      case PROTOBUF:
        serviceMethod = Source;
        break;
      case JSON:
        serviceMethod = Source_JSON;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod).compose(req -> {
      req.end(request);
      return req.response().flatMap(resp -> {
        if (resp.status() != null && resp.status() != GrpcStatus.OK) {
          return Future.failedFuture("Invalid gRPC status " + resp.status());
        } else {
          return Future.succeededFuture(resp);
        }
      });
    });
  }

  public Future<examples.Empty> sink(Handler<WriteStream<examples.Item>> request) {
    ServiceMethod<examples.Empty,examples.Item> serviceMethod;
    switch(wireFormat) {
      case PROTOBUF:
        serviceMethod = Sink;
        break;
      case JSON:
        serviceMethod = Sink_JSON;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod).compose(req -> {
      request.handle(req);
      return req.response().compose(resp -> resp.last());
    });
  }

  public Future<ReadStream<examples.Item>> pipe(Handler<WriteStream<examples.Item>> request) {
    ServiceMethod<examples.Item,examples.Item> serviceMethod;
    switch(wireFormat) {
      case PROTOBUF:
        serviceMethod = Pipe;
        break;
      case JSON:
        serviceMethod = Pipe_JSON;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod).compose(req -> {
      request.handle(req);
      return req.response().flatMap(resp -> {
        if (resp.status() != null && resp.status() != GrpcStatus.OK) {
          return Future.failedFuture("Invalid gRPC status " + resp.status());
        } else {
          return Future.succeededFuture(resp);
        }
      });
    });
  }

}
