package io.vertx.grpc.eventbus.tests;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventBusGrpcLoadBalancingTest extends EventBusGrpcTestBase {

  @Test
  public void testCancellationWriteFailureShouldDisposeTheEndpoint(TestContext should) throws Exception {
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx).await();
    Map<Context, Integer> distributions = new ConcurrentHashMap<>();

    int numEndpoints = 4;
    int numReq = 25;

    vertx.deployVerticle(() -> new VerticleBase() {
      EventBusGrpcServer server;
      @Override
      public Future<?> start() {
        return EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()).andThen(ar -> {
          if (ar.succeeded()) {
            server = ar.result();
            server.callHandler(UNARY_SERVER, request -> {
              distributions.compute(context, (ctx, prev) -> prev == null ? 1 : prev + 1);
              request.handler(msg -> {
                request.response().end(Reply.getDefaultInstance());
              });
            });
          }
        });
      }
    }, new DeploymentOptions().setInstances(numEndpoints))
      .await();

    for (int i = 0;i < numEndpoints * numReq;i++) {
      Reply reply = client.request(UNARY_CLIENT).compose(request -> {
        request.end(Request.getDefaultInstance());
        return request.response().compose(response -> response.last());
      }).await();
    }

    should.assertEquals(numEndpoints, distributions.size());
    distributions.values().forEach(count -> {
      should.assertEquals(numReq, count);
    });
  }
}
