package examples;

import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.docgen.Source;
import io.vertx.grpc.client.*;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcServer;

import java.util.concurrent.TimeUnit;

@Source
public class GrpcClientExamples {

  public void createClient(Vertx vertx) {
    GrpcClient client = GrpcClient.client(vertx);
  }

  public void sendRequest(GrpcClient client) {

    SocketAddress server = SocketAddress.inetSocketAddress(443, "example.com");
    ServiceMethod<HelloReply, HelloRequest> sayHelloMethod = VertxGreeterGrpcClient.SayHello;
    Future<GrpcClientRequest<HelloRequest, HelloReply>> fut = client.request(server, sayHelloMethod);
    fut.onSuccess(request -> {
      // The end method calls the service
      request.end(HelloRequest.newBuilder().setName("Bob").build());
    });
  }

  public void receiveResponse(GrpcClientRequest<HelloRequest, HelloReply> request) {
    request.response().onSuccess(response -> {
      Future<HelloReply> fut = response.last();
      fut.onSuccess(reply -> {
        System.out.println("Received " + reply.getMessage());
      });
    });
  }

  public void requestResponse(GrpcClient client, SocketAddress server) {
    client
      .request(server, VertxGreeterGrpcClient.SayHello).compose(request -> {
        request.end(HelloRequest
          .newBuilder()
          .setName("Bob")
          .build());
        return request.response().compose(response -> response.last());
      }).onSuccess(reply -> {
        System.out.println("Received " + reply.getMessage());
      });
  }

  public void streamingRequest(GrpcClient client, SocketAddress server) {
    client
      .request(server, VertxStreamingGrpcClient.Sink)
      .onSuccess(request -> {
      for (int i = 0;i < 10;i++) {
        request.write(Item.newBuilder().setValue("1").build());
      }
      request.end();
    });
  }

  public void streamingResponse(GrpcClient client, SocketAddress server) {
    client
      .request(server, VertxStreamingGrpcClient.Source)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .onSuccess(response -> {
        response.handler(item -> {
          // Process item
        });
        response.endHandler(v -> {
          // Done
        });
        response.exceptionHandler(err -> {
          // Something went bad
        });
      });
  }

  public void requestFlowControl(GrpcClientRequest<Item, Empty> request, Item item) {
    if (request.writeQueueFull()) {
      request.drainHandler(v -> {
        // Writable again
      });
    } else {
      request.write(item);
    }
  }

  public void responseFlowControl(Vertx vertx, GrpcClientResponse<Empty, Item> response, Item item) {
    // Pause the response
    response.pause();

    performAsyncOperation().onComplete(ar -> {
      // And then resume
      response.resume();
    });
  }

  private Future<Void> performAsyncOperation() {
    return null;
  }

  public void requestCancellation(GrpcClientRequest<Item, Empty> request) {
    request.cancel();
  }

  public void jsonWireFormat01(GrpcClient client, SocketAddress server) {
    client
      .request(server, VertxGreeterGrpcClient.SayHello_JSON).compose(request -> {
        request.end(HelloRequest
          .newBuilder()
          .setName("Bob")
          .build());
        return request.response().compose(response -> response.last());
      }).onSuccess(reply -> {
        System.out.println("Received " + reply.getMessage());
      });
  }

  public void jsonWireFormat02(GrpcClient client, SocketAddress server) {
    ServiceMethod<JsonObject, JsonObject> sayHello = ServiceMethod.client(
      ServiceName.create("helloworld", "Greeter"),
      "SayHello",
      GrpcMessageEncoder.JSON_OBJECT,
      GrpcMessageDecoder.JSON_OBJECT
    );
    client
      .request(server, sayHello).compose(request -> {
        request.end(new JsonObject().put("name", "Bob"));
        return request.response().compose(response -> response.last());
      }).onSuccess(reply -> {
        System.out.println("Received " + reply.getString("message"));
      });
  }

  public void requestCompression(GrpcClientRequest<Item, Empty> request) {
    request.encoding("gzip");

    // Write items after encoding has been defined
    request.write(Item.newBuilder().setValue("item-1").build());
    request.write(Item.newBuilder().setValue("item-2").build());
    request.write(Item.newBuilder().setValue("item-3").build());
  }

  public void requestWithDeadline(Vertx vertx) {

    // Set a 10 seconds timeout that will be sent to the gRPC service
    // Let the client schedule a deadline
    GrpcClient client = GrpcClient.client(vertx, new GrpcClientOptions()
      .setTimeout(10)
      .setTimeoutUnit(TimeUnit.SECONDS)
      .setScheduleDeadlineAutomatically(true));
  }

  public void requestWithDeadline2(GrpcClient client, SocketAddress server, MethodDescriptor<HelloRequest, HelloReply> sayHelloMethod) {

    Future<GrpcClientRequest<HelloRequest, HelloReply>> fut = client.request(server, VertxGreeterGrpcClient.SayHello);
    fut.onSuccess(request -> {

      request
        // Given this request, set a 10 seconds timeout that will be sent to the gRPC service
        .timeout(10, TimeUnit.SECONDS);

      request.end(HelloRequest.newBuilder().setName("Bob").build());
    });

  }

  public void protobufLevelAPI(GrpcClient client, Buffer protoHello, SocketAddress server) {

    Future<GrpcClientRequest<Buffer, Buffer>> requestFut = client.request(server);

    requestFut.onSuccess(request -> {

      // Set the service name and the method to call
      request.serviceName(ServiceName.create("helloworld", "Greeter"));
      request.methodName("SayHello");

      // Send the protobuf request
      request.end(protoHello);

      // Handle the response
      Future<GrpcClientResponse<Buffer, Buffer>> responseFut = request.response();
      responseFut.onSuccess(response -> {
        response.handler(protoReply -> {
          // Handle the protobuf reply
        });
      });
    });
  }

  public void messageLevelAPI(GrpcClient client, Buffer protoHello, SocketAddress server) {

    Future<GrpcClientRequest<Buffer, Buffer>> requestFut = client.request(server);

    requestFut.onSuccess(request -> {

      // Set the service name and the method to call
      request.serviceName(ServiceName.create("helloworld", "Greeter"));
      request.methodName("SayHello");

      // Send the protobuf request
      request.endMessage(GrpcMessage.message("identity", protoHello));

      // Handle the response
      Future<GrpcClientResponse<Buffer, Buffer>> responseFut = request.response();
      responseFut.onSuccess(response -> {
        response.messageHandler(replyMessage -> {
          System.out.println("Got reply message encoded as " + replyMessage.encoding());
        });
      });
    });
  }

  public void createClientStub(GrpcClient grpcClient, String host, int port) {
    VertxGreeterGrpcClient client = new VertxGreeterGrpcClient(grpcClient, SocketAddress.inetSocketAddress(port, host));
  }

  public void createClientStubJson(GrpcClient grpcClient, int port, String host) {
    VertxGreeterGrpcClient client = new VertxGreeterGrpcClient(grpcClient, SocketAddress.inetSocketAddress(port, host), WireFormat.JSON);
  }

  public void unaryStub(VertxGreeterGrpcClient client) {
    Future<HelloReply> response = client.sayHello(HelloRequest.newBuilder().setName("John").build());

    response.onSuccess(result -> System.out.println("Service responded: " + response.result().getMessage()));

    response.onFailure(err -> System.out.println("Service failure: " + response.cause().getMessage()));
  }

  public void streamingRequestStub(VertxStreamingGrpcClient client) {
    Future<Empty> response = client.sink(stream -> {
      stream.write(Item.newBuilder().setValue("Value 1").build());
      stream.write(Item.newBuilder().setValue("Value 2").build());
      stream.end(Item.newBuilder().setValue("Value 3").build());
    });
  }

  public void streamingResponseStub(VertxStreamingGrpcClient client) {
    Future<ReadStream<Item>> response = client.source(Empty.getDefaultInstance());

    response.onSuccess(stream -> stream
      .handler(item -> System.out.println("Item " + item.getValue()))
      .exceptionHandler(err -> System.out.println("Stream failed " + err.getMessage()))
      .endHandler(v -> System.out.println("Stream ended")));

    response.onFailure(err -> System.out.println("Service failure: " + err.getMessage()));
  }
}
