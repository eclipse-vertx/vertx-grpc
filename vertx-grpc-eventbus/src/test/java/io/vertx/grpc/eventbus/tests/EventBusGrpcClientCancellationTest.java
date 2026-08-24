package io.vertx.grpc.eventbus.tests;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcErrorException;
import io.vertx.grpc.common.tests.Empty;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class EventBusGrpcClientCancellationTest extends EventBusGrpcTestBase {

  EventBusGrpcServer server;
  EventBusGrpcClient client;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);
    client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()).await();
    server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions().setInitialWindowSize(8)).await();
  }

  @Test
  public void testClientUnaryCancelEarly(TestContext should) {
    server.callHandler(UNARY_SERVER, request -> {
    });
    GrpcClientRequest<Request, Reply> request = client.request(UNARY_CLIENT).await();
    request.cancel();
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }

  @Test
  public void testClientUnaryCancelBeforeEnd(TestContext should) {
    server.callHandler(UNARY_SERVER, request -> {
    });
    GrpcClientRequest<Request, Reply> request = client.request(UNARY_CLIENT).await();
    request.write(Request.getDefaultInstance()).await();
    request.cancel();
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }

  @Test
  public void testClientUnaryCancelBeforeAfterEnd(TestContext should) {
    AtomicReference<GrpcServerRequest<Request, Reply>> serverRequestRef = new AtomicReference<>();
    Async async = should.async();
    server.callHandler(UNARY_SERVER, request -> {
      serverRequestRef.set(request);
      async.complete();
    });
    GrpcClientRequest<Request, Reply> request = client.request(UNARY_CLIENT).await();
    request.end(Request.getDefaultInstance());
    async.awaitSuccess(20_000);
    request.cancel();
    serverRequestRef
      .get()
      .response()
      .end(Reply.getDefaultInstance());
    request.cancellation().onComplete(should.asyncAssertFailure(err -> {
      should.assertFalse(request.isCancelled());
    }));
  }

  @Test
  public void testClientSinkCancelEarly(TestContext should) {
    server.callHandler(SINK_SERVER, request -> {
    });
    GrpcClientRequest<Request, Empty> request = client.request(SINK_CLIENT).await();
    request.cancel();
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }

  @Test
  public void testClientSinkCancelBeforeEnd(TestContext should) {
    AtomicReference<GrpcServerRequest<Request, Empty>> serverRequestRef = new AtomicReference<>();
    Async latch = should.async();
    server.callHandler(SINK_SERVER, request -> {
      serverRequestRef.set(request);
      latch.complete();
    });
    GrpcClientRequest<Request, Empty> request = client.request(SINK_CLIENT).await();
    request.write(Request.getDefaultInstance()).await();
    request.cancel();
    latch.awaitSuccess(20_000);
    serverRequestRef
      .get()
      .response()
      .writeHead();
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }

  @Test
  public void testClientSinkCancelBeforeAfterEnd(TestContext should) {
    AtomicReference<GrpcServerRequest<Request, Empty>> serverRequestRef = new AtomicReference<>();
    Async async = should.async();
    server.callHandler(SINK_SERVER, request -> {
      serverRequestRef.set(request);
      async.complete();
    });
    GrpcClientRequest<Request, Empty> request = client.request(SINK_CLIENT).await();
    request.end(Request.getDefaultInstance());
    async.awaitSuccess(20_000);
    request.cancel();
    serverRequestRef
      .get()
      .response()
      .end(Empty.getDefaultInstance());
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }

  @Test
  public void testClientSourceCancelEarly(TestContext should) {
    server.callHandler(SOURCE_SERVER, request -> {
    });
    GrpcClientRequest<Empty, Reply> request = client.request(SOURCE_CLIENT).await();
    request.cancel();
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }

  @Test
  public void testClientSourceCancelBeforeEnd(TestContext should) {
    server.callHandler(SOURCE_SERVER, request -> {
    });
    GrpcClientRequest<Empty, Reply> request = client.request(SOURCE_CLIENT).await();
    request.write(Empty.getDefaultInstance()).await();
    request.cancel();
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }

  @Test
  public void testClientSourceCancelBeforeAfterEnd(TestContext should) {
    AtomicReference<GrpcServerRequest<Empty, Reply>> serverRequestRef = new AtomicReference<>();
    Async async = should.async();
    server.callHandler(SOURCE_SERVER, request -> {
      serverRequestRef.set(request);
      async.complete();
    });
    GrpcClientRequest<Empty, Reply> request = client.request(SOURCE_CLIENT).await();
    request.end(Empty.getDefaultInstance());
    async.awaitSuccess(20_000);
    request.cancel();
    serverRequestRef
      .get()
      .response()
      .end(Reply.getDefaultInstance());
    try {
      request.response().await();
      should.fail();
    } catch (GrpcErrorException e) {
      should.assertEquals(GrpcError.CANCELLED, e.error());
    }
    should.assertTrue(request.isCancelled());
  }
}
