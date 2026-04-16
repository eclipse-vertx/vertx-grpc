package examples;

import examples.grpc.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.docgen.Source;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.Service;

@Source
public class GrpcEventBusExamples {

  public void createServer(Vertx vertx) {
    EventBusGrpcServer server = EventBusGrpcServer.create(vertx);
  }

  public void createClient(Vertx vertx) {
    EventBusGrpcClient client = EventBusGrpcClient.create(vertx);
  }

  public void serverWithService(Vertx vertx) {
    EventBusGrpcServer server = EventBusGrpcServer.create(vertx);

    Service service = GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    });

    server.addService(service);
  }

  public void clientWithService(Vertx vertx) {
    EventBusGrpcClient client = EventBusGrpcClient.create(vertx);

    GreeterClient greeter = GreeterGrpcClient.create(client);

    greeter.sayHello(HelloRequest.newBuilder().setName("World").build())
      .onSuccess(reply -> System.out.println("Received: " + reply.getMessage()));
  }

  public void jsonWireFormat(Vertx vertx) {
    EventBusGrpcClient client = EventBusGrpcClient.create(vertx);

    GreeterClient greeter = GreeterGrpcClient.create(client, WireFormat.JSON);

    greeter.sayHello(HelloRequest.newBuilder().setName("World").build())
      .onSuccess(reply -> System.out.println("Received: " + reply.getMessage()));
  }
}
