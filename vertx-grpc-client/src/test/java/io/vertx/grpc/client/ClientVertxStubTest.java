package io.vertx.grpc.client;


import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.VertxGreeterGrpc;
import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.io.IOException;

public class ClientVertxStubTest extends ClientTest {

  /**
   * Test when server are unavailable,  use VertxStub send request， Can correctly display exceptions
   *
   * @throws IOException
   */
  @Test
  public void testServerUnavailable(TestContext should) throws IOException {

    //not start server

//    startServer(new GreeterGrpc.GreeterImplBase() {
//      @Override
//      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
//        String name = request.getName();
//        responseObserver.onNext(HelloReply.newBuilder().setMessage(name).build());
//        responseObserver.onCompleted();
//      }
//    });

    Async test = should.async();

    //An ordinary request,
    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));
    VertxGreeterGrpc.GreeterVertxStub stub = VertxGreeterGrpc.newVertxStub(channel);
    Future<HelloReply> naturalFuture = stub.sayHello(HelloRequest.newBuilder().setName("ordinary").build());
    naturalFuture.onComplete(asyncResponse -> {
      if (asyncResponse.failed()) {
        Throwable cause = asyncResponse.cause();
        //Exceptions are now correctly prompted
        should.assertEquals(cause.getClass().getName(), "io.grpc.StatusRuntimeException");
        should.assertEquals(cause.getMessage(), "UNAVAILABLE");
        test.complete();
      }
    });
  }
}
