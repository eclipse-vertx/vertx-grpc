package examples;

import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.docgen.Source;
import io.vertx.grpc.client.*;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.client.GrpcIoClientChannel;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.ServiceName;

import java.util.concurrent.TimeUnit;

@Source
public class GrpcClientExamples {

  public void createClient(Vertx vertx) {
    GrpcIoClient client = GrpcIoClient.client(vertx);
  }

  public void sendRequest(GrpcIoClient client) {

    SocketAddress server = SocketAddress.inetSocketAddress(443, "example.com");
    MethodDescriptor<HelloRequest, HelloReply> sayHelloMethod = GreeterGrpc.getSayHelloMethod();
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

  public void requestResponse(GrpcIoClient client, SocketAddress server) {
    client
      .request(server, GreeterGrpc.getSayHelloMethod()).compose(request -> {
        request.end(HelloRequest
          .newBuilder()
          .setName("Bob")
          .build());
        return request.response().compose(response -> response.last());
      }).onSuccess(reply -> {
        System.out.println("Received " + reply.getMessage());
      });
  }

  public void streamingRequest(GrpcIoClient client, SocketAddress server) {
    client
      .request(server, StreamingGrpc.getSinkMethod())
      .onSuccess(request -> {
      for (int i = 0;i < 10;i++) {
        request.write(Item.newBuilder().setValue("1").build());
      }
      request.end();
    });
  }

  public void streamingResponse(GrpcIoClient client, SocketAddress server) {
    client
      .request(server, StreamingGrpc.getSourceMethod())
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

  public void requestCompression(GrpcClientRequest<Item, Empty> request) {
    request.encoding("gzip");

    // Write items after encoding has been defined
    request.write(Item.newBuilder().setValue("item-1").build());
    request.write(Item.newBuilder().setValue("item-2").build());
    request.write(Item.newBuilder().setValue("item-3").build());
  }

  public void stub(GrpcIoClient client) {

    GrpcIoClientChannel channel = new GrpcIoClientChannel(client, SocketAddress.inetSocketAddress(443, "example.com"));

    GreeterGrpc.GreeterStub greeter = GreeterGrpc.newStub(channel);

    StreamObserver<HelloReply> observer = new StreamObserver<HelloReply>() {
      @Override
      public void onNext(HelloReply value) {
        // Process response
      }

      @Override
      public void onCompleted() {
        // Done
      }

      @Override
      public void onError(Throwable t) {
        // Something went bad
      }
    };

    greeter.sayHello(HelloRequest.newBuilder().setName("Bob").build(), observer);
  }

  public void stubWithDeadline(GrpcIoClientChannel channel, StreamObserver<HelloReply> observer) {

    GreeterGrpc.GreeterStub greeter = GreeterGrpc.newStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

    greeter.sayHello(HelloRequest.newBuilder().setName("Bob").build(), observer);
  }

  public void requestWithDeadline(Vertx vertx) {

    // Set a 10 seconds timeout that will be sent to the gRPC service
    // Let the client schedule a deadline
    GrpcClient client = GrpcClient.client(vertx, new GrpcClientOptions()
      .setTimeout(10)
      .setTimeoutUnit(TimeUnit.SECONDS)
      .setScheduleDeadlineAutomatically(true));
  }

  public void requestWithDeadline2(GrpcIoClient client, SocketAddress server, MethodDescriptor<HelloRequest, HelloReply> sayHelloMethod) {

    Future<GrpcClientRequest<HelloRequest, HelloReply>> fut = client.request(server, sayHelloMethod);
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
}
