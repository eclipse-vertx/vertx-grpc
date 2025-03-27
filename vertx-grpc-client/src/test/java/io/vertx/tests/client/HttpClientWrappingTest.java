package io.vertx.tests.client;

import io.grpc.*;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnectOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Test;

import java.io.IOException;

public class HttpClientWrappingTest extends ClientTestBase {

  @Test
  public void testUnary(TestContext should) throws IOException {
    TestServiceGrpc.TestServiceImplBase called = new TestServiceGrpc.TestServiceImplBase() {
      @Override
      public void unary(Request request, StreamObserver<Reply> plainResponseObserver) {
        ServerCallStreamObserver<Reply> responseObserver =
          (ServerCallStreamObserver<Reply>) plainResponseObserver;
        responseObserver.onNext(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
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
      return client.request(UNARY)
        .compose(req -> {
          req.end(Request.newBuilder().setName("Julien").build());
          return req.response().compose(GrpcReadStream::last);
        }).eventually(client::close);
    }).onComplete(should.asyncAssertSuccess(reply -> {
      should.assertEquals("Hello Julien", reply.getMessage());
      async.complete();
    }));
    async.await(20_000);
  }
}
