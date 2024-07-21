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

public class GreeterGrpcServer  {

  public static final ServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = ServiceMethod.server(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.HelloRequest.parser())
  );

  public Future<examples.HelloReply> sayHello(examples.HelloRequest request) {
    throw new UnsupportedOperationException("Not implemented");
  }
  public void sayHello(examples.HelloRequest request, Promise<examples.HelloReply> response) {
    sayHello(request)
      .onSuccess(msg -> response.complete(msg))
      .onFailure(error -> response.fail(error));
  }

  public GreeterGrpcServer bind_sayHello(GrpcServer server) {
    server.callHandler(SayHello, request -> {
      Promise<examples.HelloReply> promise = Promise.promise();
      request.handler(req -> {
        try {
          sayHello(req, promise);
        } catch (RuntimeException err) {
          promise.tryFail(err);
        }
      });
      promise.future()
        .onFailure(err -> request.response().status(GrpcStatus.INTERNAL).end())
        .onSuccess(resp -> request.response().end(resp));
    });
    return this;
  }

  public GreeterGrpcServer bindAll(GrpcServer server) {
    bind_sayHello(server);
    return this;
  }
}
