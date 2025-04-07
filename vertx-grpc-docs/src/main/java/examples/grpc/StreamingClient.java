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
public interface StreamingClient extends Streaming {

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
  default Future<examples.grpc.Empty> sink(ReadStream<examples.grpc.Item> streamOfMessages) {
    io.vertx.core.streams.Pipe<examples.grpc.Item> pipe = streamOfMessages.pipe();
    return sink((result, error) -> {
        if (error == null) {
          pipe.to(result);
        } else {
          pipe.close();
        }
    });
  }

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
  default Future<ReadStream<examples.grpc.Item>> pipe(ReadStream<examples.grpc.Item> streamOfMessages) {
    io.vertx.core.streams.Pipe<examples.grpc.Item> pipe = streamOfMessages.pipe();
    return pipe((result, error) -> {
        if (error == null) {
          pipe.to(result);
        } else {
          pipe.close();
        }
    });
  }
}
