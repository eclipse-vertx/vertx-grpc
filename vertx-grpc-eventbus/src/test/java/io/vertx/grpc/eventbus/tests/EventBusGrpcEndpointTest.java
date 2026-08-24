package io.vertx.grpc.eventbus.tests;

import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.GrpcErrorException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class EventBusGrpcEndpointTest extends EventBusGrpcTestBase {

  EventBusGrpcServer server;
  EventBusGrpcClient client;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);
    client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()
      .setPingInterval(Duration.ofMillis(1))
    ).await();
    server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()).await();
  }

  @Test
  public void testCancellationWriteFailureShouldDisposeTheEndpoint(TestContext should) throws Exception {

    Async test = should.async(2);

    AtomicInteger numberOfPingFrames = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      protected Result onClientCancel(String serverAddress, String streamId) {
        return Result.failure(new Exception("could not cancel"));
      }
      @Override
      protected Result onPing(String srcAddress, String dstAddress, long data, boolean ack) {
        numberOfPingFrames.incrementAndGet();
        return super.onPing(srcAddress, dstAddress, data, ack);
      }
    });

    server.callHandler(PIPE_SERVER, request -> {
      request.handler(msg -> {
        request.response().write(Reply.getDefaultInstance());
      });
    });

    GrpcClientRequest<Request, Reply> request1 = client.request(PIPE_CLIENT).await();
    request1.write(Request.getDefaultInstance()).await();
    request1.exceptionHandler(err -> {
      request1.exceptionHandler(null);
      test.countDown();
    });
    request1.response().await();

    GrpcClientRequest<Request, Reply> request2 = client.request(PIPE_CLIENT).await();
    request2.exceptionHandler(err -> {
      request2.exceptionHandler(null);
      test.countDown();
    });
    request2.write(Request.getDefaultInstance()).await();
    request1.response().await();

    request2.cancel();
    numberOfPingFrames.set(0);
    test.awaitSuccess(20_000);
    Thread.sleep(10);
    should.assertEquals(0, numberOfPingFrames.get());
  }

  @Test
  public void testCloseClientStreamsAfterEndpointClose(TestContext should) {
    Async latch = should.async();
    AtomicReference<GrpcServerRequest<Request, Reply>> serverRequestRef = new AtomicReference<>();
    server.callHandler(UNARY_SERVER, request -> {
      serverRequestRef.set(request);
      latch.complete();
    });
    GrpcClientRequest<Request, Reply> request = client.request(UNARY_CLIENT).await();
    Future<Void> end = request.end(Request.getDefaultInstance());
    latch.awaitSuccess(20_000);
    client.close().await();
    should.assertTrue(request.response().failed());
    should.assertFalse(end.isComplete());
    serverRequestRef.get().response().end(Reply.getDefaultInstance());
    try {
      end.await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcStatus.CANCELLED, e.error().status);
    }
  }
}
