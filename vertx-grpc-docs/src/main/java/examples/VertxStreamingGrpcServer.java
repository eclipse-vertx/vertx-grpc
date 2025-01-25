package examples;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.GrpcWriteStream;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.transcoding.ServiceTranscodingOptions;
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
  public static final ServiceMethod<examples.Empty, examples.Item> Source_JSON = ServiceMethod.server(
    ServiceName.create("streaming", "Streaming"),
    "Source",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.Empty.newBuilder()));
  public static final ServiceTranscodingOptions Source_TRANSCODING = new ServiceTranscodingOptions(
    "",
    HttpMethod.valueOf("POST"),
    "/Streaming/Source",
    "",
    "",
    List.of(
    ));
  public static final ServiceMethod<examples.Item, examples.Empty> Sink = ServiceMethod.server(
    ServiceName.create("streaming", "Streaming"),
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));
  public static final ServiceMethod<examples.Item, examples.Empty> Sink_JSON = ServiceMethod.server(
    ServiceName.create("streaming", "Streaming"),
    "Sink",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));
  public static final ServiceTranscodingOptions Sink_TRANSCODING = new ServiceTranscodingOptions(
    "",
    HttpMethod.valueOf("POST"),
    "/Streaming/Sink",
    "",
    "",
    List.of(
    ));
  public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.server(
    ServiceName.create("streaming", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));
  public static final ServiceMethod<examples.Item, examples.Item> Pipe_JSON = ServiceMethod.server(
    ServiceName.create("streaming", "Streaming"),
    "Pipe",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));
  public static final ServiceTranscodingOptions Pipe_TRANSCODING = new ServiceTranscodingOptions(
    "",
    HttpMethod.valueOf("POST"),
    "/Streaming/Pipe",
    "",
    "",
    List.of(
    ));

  public static class StreamingApi {

    public ReadStream<examples.Item> source(examples.Empty request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    public void source(examples.Empty request, GrpcWriteStream<examples.Item> response) {
      source(request)
        .handler(msg -> response.write(msg))
        .endHandler(msg -> response.end())
        .resume();
    }
    public Future<examples.Empty> sink(GrpcReadStream<examples.Item> request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    public void sink(GrpcReadStream<examples.Item> request, Promise<examples.Empty> response) {
      sink(request)
        .onSuccess(msg -> response.complete(msg))
        .onFailure(error -> response.fail(error));
    }
    public ReadStream<examples.Item> pipe(GrpcReadStream<examples.Item> request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    public void pipe(GrpcReadStream<examples.Item> request, GrpcWriteStream<examples.Item> response) {
      pipe(request)
        .handler(msg -> response.write(msg))
        .endHandler(msg -> response.end())
        .resume();
    }

    public final void handle_source(io.vertx.grpc.server.GrpcServerRequest<examples.Empty, examples.Item> request) {
      request.handler(msg -> {
        try {
          source(msg, request.response());
        } catch (RuntimeException err) {
          request.response().status(GrpcStatus.INTERNAL).end();
        }
      });
    }
    public StreamingApi bind_source(GrpcServer server) {
      return bind_source(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public StreamingApi bind_source(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.Empty,examples.Item> serviceMethod;
      switch(format) {
        case PROTOBUF:
          serviceMethod = Source;
          break;
        case JSON:
          serviceMethod = Source_JSON;
          break;
        default:
          throw new AssertionError();
      }
      server.callHandler(serviceMethod, this::handle_source);
      return this;
    }
    public StreamingApi bind_source_with_transcoding(GrpcServer server) {
      return bind_source_with_transcoding(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public StreamingApi bind_source_with_transcoding(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.Empty,examples.Item> serviceMethod;
      switch(format) {
      case PROTOBUF:
        serviceMethod = Source;
        break;
      case JSON:
        serviceMethod = Source_JSON;
        break;
      default:
        throw new AssertionError();
    }
      server.callHandlerWithTranscoding(serviceMethod, this::handle_source, Source_TRANSCODING);
      return this;
    }
    public final void handle_sink(io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Empty> request) {
      Promise<examples.Empty> promise = Promise.promise();
      promise.future()
        .onFailure(err -> request.response().status(GrpcStatus.INTERNAL).end())
        .onSuccess(resp -> request.response().end(resp));
      try {
        sink(request, promise);
      } catch (RuntimeException err) {
        promise.tryFail(err);
      }
    }
    public StreamingApi bind_sink(GrpcServer server) {
      return bind_sink(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public StreamingApi bind_sink(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.Item,examples.Empty> serviceMethod;
      switch(format) {
        case PROTOBUF:
          serviceMethod = Sink;
          break;
        case JSON:
          serviceMethod = Sink_JSON;
          break;
        default:
          throw new AssertionError();
      }
      server.callHandler(serviceMethod, this::handle_sink);
      return this;
    }
    public StreamingApi bind_sink_with_transcoding(GrpcServer server) {
      return bind_sink_with_transcoding(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public StreamingApi bind_sink_with_transcoding(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.Item,examples.Empty> serviceMethod;
      switch(format) {
      case PROTOBUF:
        serviceMethod = Sink;
        break;
      case JSON:
        serviceMethod = Sink_JSON;
        break;
      default:
        throw new AssertionError();
      }
      server.callHandlerWithTranscoding(serviceMethod, this::handle_sink, Sink_TRANSCODING);
      return this;
    }
    public final void handle_pipe(io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Item> request) {
      try {
        pipe(request, request.response());
      } catch (RuntimeException err) {
        request.response().status(GrpcStatus.INTERNAL).end();
      }
    }
    public StreamingApi bind_pipe(GrpcServer server) {
      return bind_pipe(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public final StreamingApi bind_pipe(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.Item,examples.Item> serviceMethod;
      switch(format) {
        case PROTOBUF:
          serviceMethod = Pipe;
          break;
        case JSON:
          serviceMethod = Pipe_JSON;
          break;
        default:
          throw new AssertionError();
      }
      server.callHandler(serviceMethod, this::handle_pipe);
      return this;
    }
    public StreamingApi bind_pipe_with_transcoding(GrpcServer server) {
      return bind_pipe_with_transcoding(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public StreamingApi bind_pipe_with_transcoding(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.Item,examples.Item> serviceMethod;
      switch(format) {
        case PROTOBUF:
          serviceMethod = Pipe;
          break;
        case JSON:
          serviceMethod = Pipe_JSON;
          break;
        default:
          throw new AssertionError();
      }
      server.callHandlerWithTranscoding(serviceMethod, this::handle_pipe, Pipe_TRANSCODING);
      return this;
    }

    public final StreamingApi bindAll(GrpcServer server) {
      bind_source(server);
      bind_sink(server);
      bind_pipe(server);
      return this;
    }

    public final StreamingApi bindAll(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      bind_source(server, format);
      bind_sink(server, format);
      bind_pipe(server, format);
      return this;
    }

    public final StreamingApi bindAllWithTranscoding(GrpcServer server) {
      bind_source_with_transcoding(server);
      bind_sink_with_transcoding(server);
      bind_pipe_with_transcoding(server);
      return this;
    }

    public final StreamingApi bindAllWithTranscoding(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      bind_source_with_transcoding(server, format);
      bind_sink_with_transcoding(server, format);
      bind_pipe_with_transcoding(server, format);
      return this;
    }
  }
}
