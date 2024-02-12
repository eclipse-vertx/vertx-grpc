package io.vertx.grpc.client;

import io.grpc.*;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnectOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcReadStream;
import org.junit.Test;

import java.io.IOException;

public class HttpClientWrappingTest extends ClientTestBase {

  @Test
  public void testUnary(TestContext should) throws IOException {
    GreeterGrpc.GreeterImplBase called = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> plainResponseObserver) {
        ServerCallStreamObserver<HelloReply> responseObserver =
          (ServerCallStreamObserver<HelloReply>) plainResponseObserver;
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };
    startServer(called, ServerBuilder.forPort(port));
    Async async = should.async();
    HttpClientAgent agent = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false));

    // Connect -> request -> response -> close
    agent.connect(new HttpConnectOptions()
      .setPort(port)
      .setHost("localhost")
    ).compose(conn -> {
      GrpcClient client = GrpcClient.client(vertx, conn);
      return client.request(GreeterGrpc.getSayHelloMethod())
        .compose(req -> {
          req.end(HelloRequest.newBuilder().setName("Julien").build());
          return req.response().compose(GrpcReadStream::last);
        }).eventually(client::close);
    }).onComplete(should.asyncAssertSuccess(reply -> {
      should.assertEquals("Hello Julien", reply.getMessage());
      async.complete();
    }));
    async.await(20_000);
  }
}
