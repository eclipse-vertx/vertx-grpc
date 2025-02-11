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

@io.vertx.codegen.annotations.VertxGen
public interface StreamingClient {

  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  public static final ServiceMethod<examples.Item, examples.Empty> Source = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  public static final ServiceMethod<examples.Empty, examples.Item> Sink = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Empty.parser()));
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));

  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  public final class Json {
    public static final ServiceMethod<examples.Item, examples.Empty> Source = ServiceMethod.client(
      ServiceName.create("streaming", "Streaming"),
      "Source",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));
    public static final ServiceMethod<examples.Empty, examples.Item> Sink = ServiceMethod.client(
      ServiceName.create("streaming", "Streaming"),
      "Sink",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Empty.newBuilder()));
    public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.client(
      ServiceName.create("streaming", "Streaming"),
      "Pipe",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));
  }

  static StreamingClient create(GrpcClient client, SocketAddress socketAddress) {
    return new StreamingClientImpl(client, socketAddress);
  }

  static StreamingClient create(GrpcClient client, SocketAddress socketAddress, io.vertx.grpc.common.WireFormat wireFormat) {
    return new StreamingClientImpl(client, socketAddress, wireFormat);
  }

  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<GrpcReadStream<examples.Item>> source(examples.Empty request);
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<examples.Empty> sink(Handler<GrpcWriteStream<examples.Item>> request);
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<GrpcReadStream<examples.Item>> pipe(Handler<GrpcWriteStream<examples.Item>> request);

}

class StreamingClientImpl implements StreamingClient {

  private final GrpcClient client;
  private final SocketAddress socketAddress;
  private final io.vertx.grpc.common.WireFormat wireFormat;

  StreamingClientImpl(GrpcClient client, SocketAddress socketAddress) {
    this(client, socketAddress, io.vertx.grpc.common.WireFormat.PROTOBUF);
  }

  StreamingClientImpl(GrpcClient client, SocketAddress socketAddress, io.vertx.grpc.common.WireFormat wireFormat) {
    this.client = java.util.Objects.requireNonNull(client);
    this.socketAddress = java.util.Objects.requireNonNull(socketAddress);
    this.wireFormat = java.util.Objects.requireNonNull(wireFormat);
  }

  public Future<GrpcReadStream<examples.Item>> source(examples.Empty request) {
    ServiceMethod<examples.Item,examples.Empty> serviceMethod;
    switch(wireFormat) {
      case PROTOBUF:
        serviceMethod = Source;
        break;
      case JSON:
        serviceMethod = Json.Source;
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

  public Future<examples.Empty> sink(Handler<GrpcWriteStream<examples.Item>> request) {
    ServiceMethod<examples.Empty,examples.Item> serviceMethod;
    switch(wireFormat) {
      case PROTOBUF:
        serviceMethod = Sink;
        break;
      case JSON:
        serviceMethod = Json.Sink;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod).compose(req -> {
      request.handle(req);
      return req.response().compose(resp -> resp.last());
    });
  }

  public Future<GrpcReadStream<examples.Item>> pipe(Handler<GrpcWriteStream<examples.Item>> request) {
    ServiceMethod<examples.Item,examples.Item> serviceMethod;
    switch(wireFormat) {
      case PROTOBUF:
        serviceMethod = Pipe;
        break;
      case JSON:
        serviceMethod = Json.Pipe;
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
