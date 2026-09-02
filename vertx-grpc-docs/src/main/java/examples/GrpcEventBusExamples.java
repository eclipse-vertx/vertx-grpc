package examples;

import examples.grpc.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.docgen.Source;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.StatusException;

import java.time.Duration;
import java.util.Set;

@Source
public class GrpcEventBusExamples {

  public void createServer(Vertx vertx) {
    Future<EventBusGrpcServer> server = EventBusGrpcServer.server(vertx);
  }

  public void createClient(Vertx vertx) {
    Future<EventBusGrpcClient> client = EventBusGrpcClient.client(vertx);
  }

  public void serverWithService(EventBusGrpcServer server) {
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

  public void clientWithService(EventBusGrpcClient client) {
    GreeterClient greeter = GreeterGrpcClient.create(client);

    greeter.sayHello(HelloRequest.newBuilder().setName("World").build())
      .onSuccess(reply -> System.out.println("Received: " + reply.getMessage()));
  }

  public void jsonWireFormat(EventBusGrpcClient client) {
    GreeterClient greeter = GreeterGrpcClient.create(client, WireFormat.JSON);

    greeter.sayHello(HelloRequest
        .newBuilder()
        .setName("World")
        .build())
      .onSuccess(reply -> System.out.println("Received: " + reply.getMessage()));
  }

  public void streamingServer(EventBusGrpcServer server) {
    Service service = StreamingGrpcService.of(new StreamingService() {
      @Override
      protected void pipe(ReadStream<Item> request, WriteStream<Item> response) {
        request.handler(item -> response.write(item));
        request.endHandler(v -> response.end());
      }
    });

    server.addService(service);
  }

  public void streamingClient(EventBusGrpcClient client) {
    StreamingClient streaming = StreamingGrpcClient.create(client);

    streaming.pipe((stream, err) -> {
      if (err == null) {
        stream.write(Item.newBuilder().setValue("a").build());
        stream.write(Item.newBuilder().setValue("b").build());
        stream.end();
      }
    }).onSuccess(response -> response
      .handler(item -> System.out.println("Received: " + item.getValue())));
  }

  public void clientOptions() {
    EventBusGrpcClientOptions options = new EventBusGrpcClientOptions()
      .setPingInterval(Duration.ofSeconds(15))
      .setPingTimeout(Duration.ofSeconds(45))
      .setInitialWindowSize(128);
  }

  public void serverOptions(Vertx vertx) {
    EventBusGrpcServerOptions options = new EventBusGrpcServerOptions()
      .setEnabledFormats(Set.of(WireFormat.PROTOBUF))
      .setMaxPingTimeout(Duration.ofMinutes(5));
  }

  public void closing(EventBusGrpcServer server) {
    server
      .close()
      .onSuccess(v -> System.out.println("Server closed"));
  }
}
