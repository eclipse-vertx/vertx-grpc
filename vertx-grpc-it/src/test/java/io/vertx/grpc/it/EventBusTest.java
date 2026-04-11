package io.vertx.grpc.it;

import io.grpc.examples.helloworld.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.StatusException;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class EventBusTest extends GrpcTestBase {

  private EventBusGrpcClient client;
  private EventBusGrpcServer server;
  private GreeterClient greeter;

  @Override
  @Before
  public void setUp(TestContext should) {
    super.setUp(should);

    client = EventBusGrpcClient.create(vertx);

    server = EventBusGrpcServer.create(vertx);
    server.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    }));

    greeter = GreeterGrpcClient.create(client, SocketAddress.inetSocketAddress(0, "localhost"));
  }

  @Test
  public void testUnary() throws Exception {
    HelloReply reply = greeter
      .sayHello(HelloRequest.newBuilder().setName("Julien").build())
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryJson() throws Exception {
    GreeterClient jsonGreeter = GreeterGrpcClient.create(client, SocketAddress.inetSocketAddress(0, "localhost"), WireFormat.JSON);

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

    try {
      greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await(10, TimeUnit.SECONDS);
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.NOT_FOUND, e.actualStatus());
    }
  }

  @Test
  public void testServerClose() throws Exception {
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
