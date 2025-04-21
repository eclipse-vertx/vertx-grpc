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
 * <p>A client for invoking the Streaming gRPC service.</p>
 */
@io.vertx.codegen.annotations.VertxGen
public interface StreamingGrpcClient extends StreamingClient {

  /**
   * Source protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<examples.grpc.Item, examples.grpc.Empty> Source = ServiceMethod.client(
    ServiceName.create("examples.grpc", "Streaming"),
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.newBuilder()));

  /**
   * Sink protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<examples.grpc.Empty, examples.grpc.Item> Sink = ServiceMethod.client(
    ServiceName.create("examples.grpc", "Streaming"),
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Empty.newBuilder()));

  /**
   * Pipe protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<examples.grpc.Item, examples.grpc.Item> Pipe = ServiceMethod.client(
    ServiceName.create("examples.grpc", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.newBuilder()));

  /**
   * Create and return a Streaming gRPC service client. The assumed wire format is Protobuf.
   *
   * @param client the gRPC client
   * @param host   the host providing the service
   * @return the configured client
   */
  static StreamingGrpcClient create(GrpcClient client, SocketAddress host) {
    return new StreamingGrpcClientImpl(client, host);
  }

  /**
   * Create and return a Streaming gRPC service client.
   *
   * @param client     the gRPC client
   * @param host       the host providing the service
   * @param wireFormat the wire format
   * @return the configured client
   */
  static StreamingGrpcClient create(GrpcClient client, SocketAddress host, io.vertx.grpc.common.WireFormat wireFormat) {
    return new StreamingGrpcClientImpl(client, host, wireFormat);
  }
}

/**
 * The proxy implementation.
 */
class StreamingGrpcClientImpl implements StreamingGrpcClient {

  private final GrpcClient client;
  private final SocketAddress socketAddress;
  private final io.vertx.grpc.common.WireFormat wireFormat;

  StreamingGrpcClientImpl(GrpcClient client, SocketAddress socketAddress) {
    this(client, socketAddress, io.vertx.grpc.common.WireFormat.PROTOBUF);
  }

  StreamingGrpcClientImpl(GrpcClient client, SocketAddress socketAddress, io.vertx.grpc.common.WireFormat wireFormat) {
    this.client = java.util.Objects.requireNonNull(client);
    this.socketAddress = java.util.Objects.requireNonNull(socketAddress);
    this.wireFormat = java.util.Objects.requireNonNull(wireFormat);
  }

  public Future<ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request) {
    return client.request(socketAddress, Source).compose(req -> {
      req.format(wireFormat);
      req.end(request);
      return req.response().flatMap(resp -> {
        if (resp.status() != null && resp.status() != GrpcStatus.OK) {
          return Future.failedFuture(new io.vertx.grpc.client.InvalidStatusException(GrpcStatus.OK, resp.status()));
        } else {
          return Future.succeededFuture(resp);
        }
      });
    });
  }

  public Future<examples.grpc.Empty> sink(Completable<WriteStream<examples.grpc.Item>> completable) {
    return client.request(socketAddress, Sink)
      .andThen((res, err) -> {
        if (err == null) {
          res.format(wireFormat);
        }
        completable.complete(res, err);
      })
      .compose(request -> {
        return request.response().compose(response -> response.last());
      });
  }

  public Future<ReadStream<examples.grpc.Item>> pipe(Completable<WriteStream<examples.grpc.Item>> completable) {
    return client.request(socketAddress, Pipe)
       .andThen((res, err) -> {
        if (err == null) {
          res.format(wireFormat);
        }
        completable.complete(res, err);
      })
     .compose(req -> {
        return req.response().flatMap(resp -> {
          if (resp.status() != null && resp.status() != GrpcStatus.OK) {
            return Future.failedFuture(new io.vertx.grpc.client.InvalidStatusException(GrpcStatus.OK, resp.status()));
          } else {
            return Future.succeededFuture(resp);
          }
        });
    });
  }
}
