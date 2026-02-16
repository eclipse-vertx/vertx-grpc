package examples;

import examples.grpc.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.docgen.Source;

@Source
public class GrpcEventBusExamples {

  public void registerHandler(Vertx vertx) {
    Greeter service = new Greeter() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(
          HelloReply.newBuilder()
            .setMessage("Hello " + request.getName())
            .build());
      }
    };
    GreeterEventBusHandler handler = new GreeterEventBusHandler(service);
    handler.register(vertx.eventBus());
  }

  public void useProxy(Vertx vertx) {
    GreeterEventBusProxy proxy = new GreeterEventBusProxy(vertx);
    proxy.sayHello(HelloRequest.newBuilder().setName("World").build())
      .onSuccess(reply -> {
        System.out.println("Received: " + reply.getMessage());
      })
      .onFailure(err -> {
        System.err.println("Failed: " + err.getMessage());
      });
  }

  public void useProxyWithOptions(Vertx vertx) {
    DeliveryOptions options = new DeliveryOptions().setSendTimeout(5000);
    GreeterEventBusProxy proxy = new GreeterEventBusProxy(vertx, options);
    proxy.sayHello(HelloRequest.newBuilder().setName("World").build())
      .onSuccess(reply -> {
        System.out.println("Received: " + reply.getMessage());
      });
  }
}
