package io.vertx.grpc.it;

import io.grpc.examples.helloworld.*;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.StatusException;
import io.vertx.grpcio.client.GrpcIoClientChannel;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class EventBusTest extends GrpcTestBase {

  private static final GreeterGrpcService greeterService = new GreeterGrpcService() {
    @Override
    public Future<HelloReply> sayHello(HelloRequest request) {
      return Future.succeededFuture(HelloReply.newBuilder()
        .setMessage("Hello " + request.getName())
        .build());
    }
  };

  private EventBusGrpcClient client;
  private EventBusGrpcServer server;

  @Override
  @Before
  public void setUp(TestContext should) {
    super.setUp(should);

    client = EventBusGrpcClient.create(vertx);
    server = EventBusGrpcServer.create(vertx);
  }

  @Test
  public void testUnary() throws Exception {

    server.addService(greeterService);
    GreeterClient greeter = GreeterGrpcClient.create(client);

    HelloReply reply = greeter
      .sayHello(HelloRequest.newBuilder().setName("Julien").build())
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryIO() throws Exception {

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(new GrpcIoClientChannel(client));
    GreeterGrpc.GreeterImplBase service = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    server.addService(GrpcIoServiceBridge.bridge(service));

    HelloReply reply = stub.sayHello(HelloRequest.newBuilder().setName("Julien").build());
    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryJson() throws Exception {

    server.addService(greeterService);
    GreeterClient jsonGreeter = GreeterGrpcClient.create(client, WireFormat.JSON);

    HelloReply reply = jsonGreeter
      .sayHello(HelloRequest.newBuilder().setName("Julien").build())
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryErrorHandling() throws Exception {
    EventBusGrpcServer errorServer = EventBusGrpcServer.create(vertx);
    errorServer.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        throw new RuntimeException("Simulated error");
      }
    }));

    GreeterClient greeter = GreeterGrpcClient.create(client);

    try {
      greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build())
        .await(10, TimeUnit.SECONDS);
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.UNKNOWN, e.actualStatus());
    }
  }

  @Test
  public void testUnaryStatusException() throws Exception {
    EventBusGrpcServer errorServer = EventBusGrpcServer.create(vertx);
    errorServer.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.failedFuture(new StatusException(GrpcStatus.NOT_FOUND, "Not found"));
      }
    }));

    GreeterClient greeter = GreeterGrpcClient.create(client);
    try {
      greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await(10, TimeUnit.SECONDS);
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.NOT_FOUND, e.actualStatus());
    }
  }

  @Test
  public void testServerClose() throws Exception {

    server.addService(greeterService);
    GreeterClient greeter = GreeterGrpcClient.create(client);

    HelloReply reply = greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());

    Promise<Void> closePromise = Promise.promise();
    server.close(closePromise);
    closePromise.future().await(10, TimeUnit.SECONDS);

    try {
      greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build())
        .await(10, TimeUnit.SECONDS);
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.UNAVAILABLE, e.actualStatus());
    }
  }
}
