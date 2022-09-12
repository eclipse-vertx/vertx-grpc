package io.vertx.grpc.client;


import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.VertxGreeterGrpc;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.io.IOException;

public class ClientVertxStubTest extends ClientTest {

  /**
   *
   * @throws IOException
   */
  @Test
  public void testServerInterrupt(TestContext should) throws IOException {

    startServer(new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        String name = request.getName();
        //The simulation server suddenly disconnected
        if (name.equals("interrupt")) {
          server.shutdownNow();
        }

        responseObserver.onNext(HelloReply.newBuilder().setMessage(name).build());
        responseObserver.onCompleted();
      }
    });

    Async test = should.async(2);

    //1. An ordinary request,
    GrpcClient client = GrpcClient.client(vertx);
    GrpcClientChannel channel = new GrpcClientChannel(client, SocketAddress.inetSocketAddress(port, "localhost"));
    VertxGreeterGrpc.GreeterVertxStub stub = VertxGreeterGrpc.newVertxStub(channel);
    Future<HelloReply> naturalFuture = stub.sayHello(HelloRequest.newBuilder().setName("ordinary").build());
    naturalFuture.onComplete(asyncResponse -> {
      if (asyncResponse.succeeded()) {
        String message = asyncResponse.result().getMessage();
        should.assertEquals("ordinary", message);
        test.countDown();
      }
    });

    //2. An interrupt request, will cause the server shutdown
    stub.sayHello((HelloRequest.newBuilder().setName("interrupt").build()));


    //The server will not be interrupted immediately,  wait for complete disconnection
    try {
      while (!server.isTerminated() || !server.isShutdown()){
        Thread.sleep(100);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    //3. An ordinary request, server unavailable now
    Future<HelloReply> ordinaryFuture2 = stub.sayHello((HelloRequest.newBuilder().setName("ordinary").build()));
    ordinaryFuture2.onComplete(should.asyncAssertFailure());

    ordinaryFuture2.onComplete(asyncResponse -> {
      if (asyncResponse.failed()) {
        Throwable cause = asyncResponse.cause();
        //System.out.println(cause);
        should.assertEquals(cause.getClass().getName(),"io.grpc.StatusRuntimeException");
        should.assertEquals(cause.getMessage(), "UNAVAILABLE");
        test.complete();
      }
    });
  }
}
