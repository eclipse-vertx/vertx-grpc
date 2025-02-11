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

public class GreeterService  {

  public static final ServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = ServiceMethod.server(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.HelloRequest.parser()));

  public static final java.util.List<ServiceMethod<?, ?>> all() {
    java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
    all.add(SayHello);
    return all;
  }

  public static final class Json {

    public static final ServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = ServiceMethod.server(
      ServiceName.create("helloworld", "Greeter"),
      "SayHello",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.HelloRequest.newBuilder()));

    public static final java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      all.add(SayHello);
      return all;
    }
  }

  public static final class Transcoding {

    private static final io.vertx.grpc.transcoding.MethodTranscodingOptions SayHello_OPTIONS = new io.vertx.grpc.transcoding.MethodTranscodingOptions()
      .setSelector("")
      .setHttpMethod(HttpMethod.valueOf("GET"))
      .setPath("/v1/hello/{name}")
      .setBody("")
      .setResponseBody("")
    ;

  public static final io.vertx.grpc.transcoding.TranscodingServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = io.vertx.grpc.transcoding.TranscodingServiceMethod.server(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.HelloRequest.newBuilder()),
    SayHello_OPTIONS);

  public static final java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      all.add(SayHello);
      return all;
    }
  }

    public Future<examples.HelloReply> sayHello(examples.HelloRequest request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    public void sayHello(examples.HelloRequest request, Promise<examples.HelloReply> response) {
      sayHello(request)
        .onSuccess(msg -> response.complete(msg))
        .onFailure(error -> response.fail(error));
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
      if (SayHello == serviceMethod || Json.SayHello == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.HelloRequest, examples.HelloReply>> handler = GreeterService.this::handle_sayHello;
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

  private void handle_sayHello(io.vertx.grpc.server.GrpcServerRequest<examples.HelloRequest, examples.HelloReply> request) {
    Promise<examples.HelloReply> promise = Promise.promise();
    request.handler(msg -> {
      try {
        sayHello(msg, promise);
      } catch (RuntimeException err) {
        promise.tryFail(err);
      }
    });
    promise.future()
      .onFailure(err -> request.response().status(GrpcStatus.INTERNAL).end())
      .onSuccess(resp -> request.response().end(resp));
  }
}
