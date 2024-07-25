package examples;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.GrpcServer;

import java.util.ArrayList;
import java.util.List;

public class VertxStreamingGrpcServer  {

  public static final ServiceMethod<examples.Empty, examples.Item> Source = ServiceMethod.server(
  ServiceName.create("streaming", "Streaming"),
  "Source",
  GrpcMessageEncoder.encoder(),
  GrpcMessageDecoder.decoder(examples.Empty.parser()));
  public static final ServiceMethod<examples.Item, examples.Empty> Sink = ServiceMethod.server(
  ServiceName.create("streaming", "Streaming"),
  "Sink",
  GrpcMessageEncoder.encoder(),
  GrpcMessageDecoder.decoder(examples.Item.parser()));
  public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.server(
  ServiceName.create("streaming", "Streaming"),
  "Pipe",
  GrpcMessageEncoder.encoder(),
  GrpcMessageDecoder.decoder(examples.Item.parser()));

  public interface StreamingApi {

    default ReadStream<examples.Item> source(examples.Empty request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    default void source(examples.Empty request, WriteStream<examples.Item> response) {
      source(request)
        .handler(msg -> response.write(msg))
        .endHandler(msg -> response.end())
        .resume();
    }
    default Future<examples.Empty> sink(ReadStream<examples.Item> request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    default void sink(ReadStream<examples.Item> request, Promise<examples.Empty> response) {
      sink(request)
        .onSuccess(msg -> response.complete(msg))
        .onFailure(error -> response.fail(error));
    }
    default ReadStream<examples.Item> pipe(ReadStream<examples.Item> request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    default void pipe(ReadStream<examples.Item> request, WriteStream<examples.Item> response) {
      pipe(request)
        .handler(msg -> response.write(msg))
        .endHandler(msg -> response.end())
        .resume();
    }

    default StreamingApi bind_source(GrpcServer server) {
      server.callHandler(Source, request -> {
        request.handler(req -> {
          try {
            source(req, request.response());
          } catch (RuntimeException err) {
            request.response().status(GrpcStatus.INTERNAL).end();
          }
        });
      });
      return this;
    }
    default StreamingApi bind_sink(GrpcServer server) {
      server.callHandler(Sink, request -> {
        Promise<examples.Empty> promise = Promise.promise();
        promise.future()
          .onFailure(err -> request.response().status(GrpcStatus.INTERNAL).end())
          .onSuccess(resp -> request.response().end(resp));
        try {
          sink(request, promise);
        } catch (RuntimeException err) {
          promise.tryFail(err);
        }
      });
      return this;
    }
    default StreamingApi bind_pipe(GrpcServer server) {
      server.callHandler(Pipe, request -> {
        try {
          pipe(request, request.response());
        } catch (RuntimeException err) {
          request.response().status(GrpcStatus.INTERNAL).end();
        }
      });
      return this;
    }

    default StreamingApi bindAll(GrpcServer server) {
      bind_source(server);
      bind_sink(server);
      bind_pipe(server);
      return this;
    }
  }
}
