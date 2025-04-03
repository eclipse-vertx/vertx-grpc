package io.vertx.grpc.sandbox.streaming;

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
public interface StreamingGrpcClient extends Streaming {

  /**
   * Unary protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Item> Unary = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Unary",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(io.vertx.grpc.sandbox.streaming.Item.parser()));

  /**
   * Source protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Empty> Source = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(io.vertx.grpc.sandbox.streaming.Item.parser()));

  /**
   * Sink protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<io.vertx.grpc.sandbox.streaming.Empty, io.vertx.grpc.sandbox.streaming.Item> Sink = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(io.vertx.grpc.sandbox.streaming.Empty.parser()));

  /**
   * Pipe protobuf RPC client service method.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Item> Pipe = ServiceMethod.client(
    ServiceName.create("streaming", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(io.vertx.grpc.sandbox.streaming.Item.parser()));

  /**
   * Json client service methods.
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  final class Json {

    /**
     * Unary json RPC client service method.
     */
    public static final ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Item> Unary = ServiceMethod.client(
      ServiceName.create("streaming", "Streaming"),
      "Unary",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> io.vertx.grpc.sandbox.streaming.Item.newBuilder()));

    /**
     * Source json RPC client service method.
     */
    public static final ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Empty> Source = ServiceMethod.client(
      ServiceName.create("streaming", "Streaming"),
      "Source",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> io.vertx.grpc.sandbox.streaming.Item.newBuilder()));

    /**
     * Sink json RPC client service method.
     */
    public static final ServiceMethod<io.vertx.grpc.sandbox.streaming.Empty, io.vertx.grpc.sandbox.streaming.Item> Sink = ServiceMethod.client(
      ServiceName.create("streaming", "Streaming"),
      "Sink",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> io.vertx.grpc.sandbox.streaming.Empty.newBuilder()));

    /**
     * Pipe json RPC client service method.
     */
    public static final ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Item> Pipe = ServiceMethod.client(
      ServiceName.create("streaming", "Streaming"),
      "Pipe",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> io.vertx.grpc.sandbox.streaming.Item.newBuilder()));
  }

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

  /**
   * Calls the Unary RPC service method.
   *
   * @param request the io.vertx.grpc.sandbox.streaming.Item request message
   * @return a future of the io.vertx.grpc.sandbox.streaming.Item response message
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<io.vertx.grpc.sandbox.streaming.Item> unary(io.vertx.grpc.sandbox.streaming.Item request);

  /**
   * Calls the Source RPC service method.
   *
   * @param request the io.vertx.grpc.sandbox.streaming.Empty request message
   * @return a future of the io.vertx.grpc.sandbox.streaming.Item response messages
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> source(io.vertx.grpc.sandbox.streaming.Empty request);

  /**
   * Calls the Sink RPC service method.
   *
   * @param completable a completable that will be passed a stream to which the io.vertx.grpc.sandbox.streaming.Item request messages can be written to.
   * @return a future of the io.vertx.grpc.sandbox.streaming.Empty response message
   */
  @io.vertx.codegen.annotations.GenIgnore
  Future<io.vertx.grpc.sandbox.streaming.Empty> sink(Completable<WriteStream<io.vertx.grpc.sandbox.streaming.Item>> completable);

  /**
   * Calls the Sink RPC service method.
   *
   * @param streamOfMessages a stream of messages to be sent to the service
   * @return a future of the io.vertx.grpc.sandbox.streaming.Empty response message
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<io.vertx.grpc.sandbox.streaming.Empty> sink(ReadStream<io.vertx.grpc.sandbox.streaming.Item> streamOfMessages);

  /**
   * Calls the Pipe RPC service method.
   *
   * @param compltable a completable that will be passed a stream to which the io.vertx.grpc.sandbox.streaming.Item request messages can be written to.
   * @return a future of the io.vertx.grpc.sandbox.streaming.Item response messages
   */
  @io.vertx.codegen.annotations.GenIgnore
  Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> pipe(Completable<WriteStream<io.vertx.grpc.sandbox.streaming.Item>> completable);

  /**
   * Calls the Pipe RPC service method.
   *
    * @param streamOfMessages a stream of messages to be sent to the service
   * @return a future of the io.vertx.grpc.sandbox.streaming.Item response messages
   */
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> pipe(ReadStream<io.vertx.grpc.sandbox.streaming.Item> streamOfMessages);
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

  public Future<io.vertx.grpc.sandbox.streaming.Item> unary(io.vertx.grpc.sandbox.streaming.Item request) {
    ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Item> serviceMethod;
    switch (wireFormat) {
      case PROTOBUF:
        serviceMethod = Unary;
        break;
      case JSON:
        serviceMethod = Json.Unary;
        break;
      default:
        throw new AssertionError();
    }
    return client.request(socketAddress, serviceMethod).compose(req -> {
      req.end(request);
      return req.response().compose(resp -> resp.last());
    });
  }

  public Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> source(io.vertx.grpc.sandbox.streaming.Empty request) {
    ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Empty> serviceMethod;
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

  public Future<io.vertx.grpc.sandbox.streaming.Empty> sink(Completable<WriteStream<io.vertx.grpc.sandbox.streaming.Item>> completable) {
    ServiceMethod<io.vertx.grpc.sandbox.streaming.Empty, io.vertx.grpc.sandbox.streaming.Item> serviceMethod;
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

  public Future<io.vertx.grpc.sandbox.streaming.Empty> sink(ReadStream<io.vertx.grpc.sandbox.streaming.Item> request) {
    io.vertx.core.streams.Pipe<io.vertx.grpc.sandbox.streaming.Item> pipe = request.pipe();
    return sink((result, error) -> {
        if (error == null) {
          pipe.to(result);
        } else {
          pipe.close();
        }
    });
  }

  public Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> pipe(Completable<WriteStream<io.vertx.grpc.sandbox.streaming.Item>> completable) {
    ServiceMethod<io.vertx.grpc.sandbox.streaming.Item, io.vertx.grpc.sandbox.streaming.Item> serviceMethod;
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

  public Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> pipe(ReadStream<io.vertx.grpc.sandbox.streaming.Item> request) {
    io.vertx.core.streams.Pipe<io.vertx.grpc.sandbox.streaming.Item> pipe = request.pipe();
    return pipe((result, error) -> {
        if (error == null) {
          pipe.to(result);
        } else {
          pipe.close();
        }
    });
  }
}
