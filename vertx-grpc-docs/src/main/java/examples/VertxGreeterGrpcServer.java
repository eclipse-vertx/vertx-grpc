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

public class VertxGreeterGrpcServer  {

  public static final ServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = ServiceMethod.server(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.HelloRequest.parser()));
  public static final ServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello_JSON = ServiceMethod.server(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.json(),
    GrpcMessageDecoder.json(() -> examples.HelloRequest.newBuilder()));
  public static final ServiceTranscodingOptions SayHello_TRANSCODING = new ServiceTranscodingOptions(
    "",
    HttpMethod.valueOf("POST"),
    "/Greeter/SayHello",
    "",
    "",
    List.of(
    ));

  public static class GreeterApi {

    public Future<examples.HelloReply> sayHello(examples.HelloRequest request) {
      throw new UnsupportedOperationException("Not implemented");
    }
    public void sayHello(examples.HelloRequest request, Promise<examples.HelloReply> response) {
      sayHello(request)
        .onSuccess(msg -> response.complete(msg))
        .onFailure(error -> response.fail(error));
    }

    public final void handle_sayHello(io.vertx.grpc.server.GrpcServerRequest<examples.HelloRequest, examples.HelloReply> request) {
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
    public GreeterApi bind_sayHello(GrpcServer server) {
      return bind_sayHello(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public GreeterApi bind_sayHello(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.HelloRequest,examples.HelloReply> serviceMethod;
      switch(format) {
        case PROTOBUF:
          serviceMethod = SayHello;
          break;
        case JSON:
          serviceMethod = SayHello_JSON;
          break;
        default:
          throw new AssertionError();
      }
      server.callHandler(serviceMethod, this::handle_sayHello);
      return this;
    }
    public GreeterApi bind_sayHello_with_transcoding(GrpcServer server) {
      return bind_sayHello_with_transcoding(server, io.vertx.grpc.common.WireFormat.PROTOBUF);
    }
    public GreeterApi bind_sayHello_with_transcoding(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      ServiceMethod<examples.HelloRequest,examples.HelloReply> serviceMethod;
      switch(format) {
        case PROTOBUF:
          serviceMethod = SayHello;
          break;
        case JSON:
          serviceMethod = SayHello_JSON;
          break;
        default:
          throw new AssertionError();
      }
      server.callHandlerWithTranscoding(serviceMethod, this::handle_sayHello, SayHello_TRANSCODING);
      return this;
    }

    public final GreeterApi bindAll(GrpcServer server) {
      bind_sayHello(server);
      return this;
    }

    public final GreeterApi bindAll(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      bind_sayHello(server, format);
      return this;
    }

    public final GreeterApi bindAllWithTranscoding(GrpcServer server) {
      bind_sayHello_with_transcoding(server);
      return this;
    }

    public final GreeterApi bindAllWithTranscoding(GrpcServer server, io.vertx.grpc.common.WireFormat format) {
      bind_sayHello_with_transcoding(server, format);
      return this;
    }
  }
}
