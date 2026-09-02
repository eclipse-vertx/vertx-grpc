package io.vertx.grpc.eventbus.tests;

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.impl.CleanableEventBusGrpcClient;
import io.vertx.grpc.eventbus.impl.EventBusGrpcClientEndpoint;
import io.vertx.grpc.eventbus.impl.EventBusGrpcServerEndpoint;
import org.junit.Before;
import org.junit.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VertxResourceTest extends EventBusGrpcTestBase  {

  private static final Runner runner = new Runner(new OptionsBuilder().shouldDoGC(true).build());
  private EventBusGrpcServer server;
  private EventBusGrpcClient client;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    server = EventBusGrpcServer.server(vertx).await();
    client = EventBusGrpcClient.client(vertx).await();
  }

  @Test
  public void testClient() {
    server.callHandler(UNARY_SERVER, request -> {
      request.endHandler(v -> request.response().end(Reply.getDefaultInstance()));
    });

    runner.runSystemGC();
    client.request(UNARY_CLIENT).await();
    EventBusGrpcClientEndpoint realClient = ((CleanableEventBusGrpcClient) client).unwrap();
    String address = realClient.address();
    client = null;
    runner.runSystemGC();
    try {
      vertx.eventBus().request(address, "").await();
      fail();
    } catch (ReplyException e) {
      assertEquals(ReplyFailure.NO_HANDLERS, e.failureType());
    }
  }

  @Test
  public void testServer() {
    AtomicReference<EventBusGrpcServer> ref = new AtomicReference<>();
    String id = vertx.deployVerticle(ctx -> EventBusGrpcServer.server(vertx).andThen(ar -> {
      if ((ar.succeeded())) {
        ref.set(ar.result());
      }
    })).await();
    String address = ((EventBusGrpcServerEndpoint) ref.get()).address();
    vertx.undeploy(id).await();
    try {
      vertx.eventBus().request(address, "").await();
      fail();
    } catch (ReplyException e) {
      assertEquals(ReplyFailure.NO_HANDLERS, e.failureType());
    }
  }
}
