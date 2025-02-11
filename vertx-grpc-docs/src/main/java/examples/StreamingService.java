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
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.GrpcServer;

import java.util.ArrayList;
import java.util.List;

public class StreamingService  {

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

  public static final java.util.List<ServiceMethod<?, ?>> all() {
    java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
    all.add(Source);
    all.add(Sink);
    all.add(Pipe);
    return all;
  }

  public static final class Json {

    public static final ServiceMethod<examples.Empty, examples.Item> Source = ServiceMethod.server(
      ServiceName.create("streaming", "Streaming"),
      "Source",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Empty.newBuilder()));

    public static final ServiceMethod<examples.Item, examples.Empty> Sink = ServiceMethod.server(
      ServiceName.create("streaming", "Streaming"),
      "Sink",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));

    public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.server(
      ServiceName.create("streaming", "Streaming"),
      "Pipe",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));

    public static final java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      all.add(Source);
      all.add(Sink);
      all.add(Pipe);
      return all;
    }
  }

  public static final class Transcoding {

  public static final java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      return all;
    }
  }

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

  public class Binder {
    private final List<ServiceMethod<?, ?>> serviceMethods;
    private Binder(List<ServiceMethod<?, ?>> serviceMethods) {
      this.serviceMethods = serviceMethods;
    }
    private void validate() {
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        if (resolveHandler(serviceMethod) == null) {
          throw new IllegalArgumentException("Invalid service method:" + serviceMethod);
        }
      }
    }
    private <Req, Resp> Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
      if (Source == serviceMethod || Json.Source == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.Empty, examples.Item>> handler = StreamingService.this::handle_source;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      if (Sink == serviceMethod || Json.Sink == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Empty>> handler = StreamingService.this::handle_sink;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      if (Pipe == serviceMethod || Json.Pipe == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Item>> handler = StreamingService.this::handle_pipe;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      return null;
    }
    private <Req, Resp> void bindHandler(GrpcServer server, ServiceMethod<Req, Resp> serviceMethod) {
      Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> handler = resolveHandler(serviceMethod);
      server.callHandler(serviceMethod, handler);
    }
    public void to(GrpcServer server) {
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        bindHandler(server, serviceMethod);
      }
    }
  }

  public Binder bind(List<ServiceMethod<?, ?>> serviceMethods) {
    Binder binder = new Binder(serviceMethods);
    binder.validate();
    return binder;
  }

  public Binder bind(ServiceMethod<?, ?>... serviceMethods) {
    return bind(java.util.Arrays.asList(serviceMethods));
  }

  private void handle_source(io.vertx.grpc.server.GrpcServerRequest<examples.Empty, examples.Item> request) {
    request.handler(msg -> {
      try {
        source(msg, request.response());
      } catch (RuntimeException err) {
        request.response().status(GrpcStatus.INTERNAL).end();
      }
    });
  }
  private void handle_sink(io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Empty> request) {
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
  private void handle_pipe(io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Item> request) {
    try {
      pipe(request, request.response());
    } catch (RuntimeException err) {
      request.response().status(GrpcStatus.INTERNAL).end();
    }
  }
}
