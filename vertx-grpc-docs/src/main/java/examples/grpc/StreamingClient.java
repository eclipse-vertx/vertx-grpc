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
public interface StreamingClient {

  /**
   * Source protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<examples.grpc.Item, examples.grpc.Empty> Source = ServiceMethod.client(
    ServiceName.create("examples.grpc", "Streaming"),
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.parser()));

  /**
   * Sink protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<examples.grpc.Empty, examples.grpc.Item> Sink = ServiceMethod.client(
    ServiceName.create("examples.grpc", "Streaming"),
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Empty.parser()));

  /**
   * Pipe protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<examples.grpc.Item, examples.grpc.Item> Pipe = ServiceMethod.client(
    ServiceName.create("examples.grpc", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.parser()));

  /**
   * Json client service methods.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  final class Json {

    /**
     * Source json RPC client service method.
     */
    public static final ServiceMethod<examples.grpc.Item, examples.grpc.Empty> Source = ServiceMethod.client(
      ServiceName.create("examples.grpc", "Streaming"),
      "Source",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.Item.newBuilder()));

    /**
     * Sink json RPC client service method.
     */
    public static final ServiceMethod<examples.grpc.Empty, examples.grpc.Item> Sink = ServiceMethod.client(
      ServiceName.create("examples.grpc", "Streaming"),
      "Sink",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.Empty.newBuilder()));

    /**
     * Pipe json RPC client service method.
     */
    public static final ServiceMethod<examples.grpc.Item, examples.grpc.Item> Pipe = ServiceMethod.client(
      ServiceName.create("examples.grpc", "Streaming"),
      "Pipe",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.Item.newBuilder()));
  }

  /**
   * Create and return a Streaming gRPC service client. The assumed wire format is Protobuf.
   *
   * @param client the gRPC client
   * @param host   the host providing the service
   * @return the configured client
   */
  static StreamingClient create(GrpcClient client, SocketAddress host) {
    return new StreamingClientImpl(client, host);
  }

  /**
   * Create and return a Streaming gRPC service client.
   *
   * @param client     the gRPC client
   * @param host       the host providing the service
   * @param wireFormat the wire format
   * @return the configured client
   */
  static StreamingClient create(GrpcClient client, SocketAddress host, io.vertx.grpc.common.WireFormat wireFormat) {
    return new StreamingClientImpl(client, host, wireFormat);
  }

  /**
   * Calls the Source RPC service method.
   *
   * @param request the examples.grpc.Empty request message
   * @return a future of the examples.grpc.Item response messages
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request);

  /**
   * Calls the Sink RPC service method.
   *
   * @param completable a completable that will be passed a stream to which the examples.grpc.Item request messages can be written to.
   * @return a future of the examples.grpc.Empty response message
   */
  @io.vertx.codegen.annotations.GenIgnore
  Future<examples.grpc.Empty> sink(Completable<WriteStream<examples.grpc.Item>> completable);

  /**
   * Calls the Sink RPC service method.
   *
   * @param streamOfMessages a stream of messages to be sent to the service
   * @return a future of the examples.grpc.Empty response message
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<examples.grpc.Empty> sink(ReadStream<examples.grpc.Item> streamOfMessages);

  /**
   * Calls the Pipe RPC service method.
   *
   * @param compltable a completable that will be passed a stream to which the examples.grpc.Item request messages can be written to.
   * @return a future of the examples.grpc.Item response messages
   */
  @io.vertx.codegen.annotations.GenIgnore
  Future<ReadStream<examples.grpc.Item>> pipe(Completable<WriteStream<examples.grpc.Item>> completable);

  /**
   * Calls the Pipe RPC service method.
   *
    * @param streamOfMessages a stream of messages to be sent to the service
   * @return a future of the examples.grpc.Item response messages
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<ReadStream<examples.grpc.Item>> pipe(ReadStream<examples.grpc.Item> streamOfMessages);
}

/**
 * The proxy implementation.
 */
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

  public Future<ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request) {
    ServiceMethod<examples.grpc.Item, examples.grpc.Empty> serviceMethod;
    switch (wireFormat) {
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

  public Future<examples.grpc.Empty> sink(Completable<WriteStream<examples.grpc.Item>> completable) {
    ServiceMethod<examples.grpc.Empty, examples.grpc.Item> serviceMethod;
    switch (wireFormat) {
      case PROTOBUF:
        serviceMethod = Sink;
        break;
      case JSON:
        serviceMethod = Json.Sink;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod)
      .andThen(completable)
      .compose(request -> {
      return request.response().compose(response -> response.last());
    });
  }

  public Future<examples.grpc.Empty> sink(ReadStream<examples.grpc.Item> request) {
    io.vertx.core.streams.Pipe<examples.grpc.Item> pipe = request.pipe();
    return sink((result, error) -> {
        if (error == null) {
          pipe.to(result);
        } else {
          pipe.close();
        }
    });
  }

  public Future<ReadStream<examples.grpc.Item>> pipe(Completable<WriteStream<examples.grpc.Item>> completable) {
    ServiceMethod<examples.grpc.Item, examples.grpc.Item> serviceMethod;
    switch (wireFormat) {
      case PROTOBUF:
        serviceMethod = Pipe;
        break;
      case JSON:
        serviceMethod = Json.Pipe;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod)
      .andThen(completable)
      .compose(req -> {
        return req.response().flatMap(resp -> {
          if (resp.status() != null && resp.status() != GrpcStatus.OK) {
            return Future.failedFuture("Invalid gRPC status " + resp.status());
          } else {
            return Future.succeededFuture(resp);
          }
        });
    });
  }

  public Future<ReadStream<examples.grpc.Item>> pipe(ReadStream<examples.grpc.Item> request) {
    io.vertx.core.streams.Pipe<examples.grpc.Item> pipe = request.pipe();
    return pipe((result, error) -> {
        if (error == null) {
          pipe.to(result);
        } else {
          pipe.close();
        }
    });
  }
}
