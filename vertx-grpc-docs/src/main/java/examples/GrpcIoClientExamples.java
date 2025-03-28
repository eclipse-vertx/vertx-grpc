package examples;

import examples.grpc.GreeterGrpc;
import examples.grpc.HelloReply;
import examples.grpc.HelloRequest;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.docgen.Source;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.client.GrpcIoClientChannel;

import java.util.concurrent.TimeUnit;

@Source
public class GrpcIoClientExamples {

  public void createClient(Vertx vertx) {
    GrpcIoClient client = GrpcIoClient.client(vertx);
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
}
