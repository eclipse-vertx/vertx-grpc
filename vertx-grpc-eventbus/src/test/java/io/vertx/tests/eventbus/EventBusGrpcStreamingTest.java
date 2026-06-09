package io.vertx.tests.eventbus;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.*;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.*;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EventBusGrpcStreamingTest extends GrpcTestBase {

  private static final ServiceMethod<Empty, Reply> SOURCE_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Source", MethodType.SERVER_STREAMING, TestConstants.REPLY_ENC, TestConstants.EMPTY_DEC);
  private static final ServiceMethod<Request, Empty> SINK_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Sink", MethodType.CLIENT_STREAMING, TestConstants.EMPTY_ENC, TestConstants.REQUEST_DEC);
  private static final ServiceMethod<Request, Reply> PIPE_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Pipe", MethodType.BIDI, TestConstants.REPLY_ENC, TestConstants.REQUEST_DEC);

  private static final ServiceMethod<Reply, Empty> SOURCE_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Source", MethodType.SERVER_STREAMING, TestConstants.EMPTY_ENC, TestConstants.REPLY_DEC);
  private static final ServiceMethod<Empty, Request> SINK_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Sink", MethodType.CLIENT_STREAMING, TestConstants.REQUEST_ENC, TestConstants.EMPTY_DEC);
  private static final ServiceMethod<Reply, Request> PIPE_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Pipe", MethodType.BIDI, TestConstants.REQUEST_ENC, TestConstants.REPLY_DEC);

  private EventBusGrpcServer server;
  private EventBusGrpcClient client;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    server = EventBusGrpcServer.server(vertx);
    client = EventBusGrpcClient.client(vertx);
  }

  private static <T> Future<List<T>> collect(GrpcReadStream<T> stream) {
    Promise<List<T>> promise = Promise.promise();
    List<T> list = new ArrayList<>();
    stream.handler(list::add);
    stream.endHandler(v -> promise.tryComplete(list));
    stream.exceptionHandler(promise::tryFail);
    return promise.future();
  }

  @Test
  public void testServerStreaming() throws Exception {
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      for (int i = 0; i < 5; i++) {
        request.response().write(Reply.newBuilder().setMessage("item-" + i).build());
      }
      request.response().end();
    }));

    List<Reply> replies = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(10, TimeUnit.SECONDS);

    assertEquals(5, replies.size());
    assertEquals("item-0", replies.get(0).getMessage());
    assertEquals("item-4", replies.get(4).getMessage());
  }

  @Test
  public void testClientStreaming() throws Exception {
    StringBuilder received = new StringBuilder();
    server.callHandler(SINK_SERVER, request -> {
      request.handler(req -> received.append(req.getName()).append(','));
      request.endHandler(v -> request.response().end(Empty.getDefaultInstance()));
    });

    client.request(SINK_CLIENT)
      .compose(request -> {
        request.write(Request.newBuilder().setName("a").build());
        request.write(Request.newBuilder().setName("b").build());
        request.end(Request.newBuilder().setName("c").build());
        return request.response().compose(GrpcReadStream::last);
      })
      .await(10, TimeUnit.SECONDS);

    assertEquals("a,b,c,", received.toString());
  }

  @Test
  public void testBidi() throws Exception {
    server.callHandler(PIPE_SERVER, request -> {
      request.handler(req -> request.response().write(Reply.newBuilder().setMessage("echo-" + req.getName()).build()));
      request.endHandler(v -> request.response().end());
    });

    List<Reply> replies = client.request(PIPE_CLIENT)
      .compose(request -> {
        request.write(Request.newBuilder().setName("a").build());
        request.write(Request.newBuilder().setName("b").build());
        request.end(Request.newBuilder().setName("c").build());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(10, TimeUnit.SECONDS);

    assertEquals(3, replies.size());
    assertEquals("echo-a", replies.get(0).getMessage());
    assertEquals("echo-c", replies.get(2).getMessage());
  }

  @Test
  public void testBidiJson() throws Exception {
    server.callHandler(PIPE_SERVER, request -> {
      request.handler(req -> request.response().write(Reply.newBuilder().setMessage("echo-" + req.getName()).build()));
      request.endHandler(v -> request.response().end());
    });

    List<Reply> replies = client.request(PIPE_CLIENT)
      .compose(request -> {
        request.format(WireFormat.JSON);
        request.write(Request.newBuilder().setName("x").build());
        request.end(Request.newBuilder().setName("y").build());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(10, TimeUnit.SECONDS);

    assertEquals(2, replies.size());
    assertEquals("echo-x", replies.get(0).getMessage());
    assertEquals("echo-y", replies.get(1).getMessage());
  }

  @Test
  public void testResponseHeaders() throws Exception {
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      response.headers().set("x-meta", "v1");
      response.write(Reply.newBuilder().setMessage("a").build());
      response.end();
    }));

    AtomicReference<String> meta = new AtomicReference<>();
    List<Reply> replies = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .compose(response -> {
        // initial metadata must be visible by the time response() resolves, before the messages
        meta.set(response.headers().get("x-meta"));
        return collect(response);
      })
      .await(10, TimeUnit.SECONDS);

    assertEquals("v1", meta.get());
    assertEquals(1, replies.size());
    assertEquals("a", replies.get(0).getMessage());
  }

  @Test
  public void testServerStreamingManyMessages() throws Exception {
    int count = 500;
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      for (int i = 0; i < count; i++) {
        request.response().write(Reply.newBuilder().setMessage("n-" + i).build());
      }
      request.response().end();
    }));

    List<Reply> replies = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(20, TimeUnit.SECONDS);

    assertEquals(count, replies.size());
    assertEquals("n-0", replies.get(0).getMessage());
    assertEquals("n-499", replies.get(count - 1).getMessage());
  }

  @Test
  public void testConcurrentStreaming() throws Exception {
    server.callHandler(PIPE_SERVER, request -> {
      request.handler(req -> request.response().write(Reply.newBuilder().setMessage("echo-" + req.getName()).build()));
      request.endHandler(v -> request.response().end());
    });

    int callCount = 20;
    int perCall = 10;

    List<List<Reply>> results = new ArrayList<>(Collections.nCopies(callCount, null));
    List<Future<?>> futures = new ArrayList<>();

    for (int c = 0; c < callCount; c++) {
      int idx = c;
      String prefix = "c" + c + "-";
      Future<?> future = client.request(PIPE_CLIENT).compose(request -> {
          for (int i = 0; i < perCall; i++) {
            request.write(Request.newBuilder().setName(prefix + i).build());
          }
          request.end();
          return request.response();
        })
        .compose(EventBusGrpcStreamingTest::collect)
        .andThen(ar -> {
          if (ar.succeeded()) {
            results.set(idx, ar.result());
          }
        });
      futures.add(future);
    }

    Future.all(futures).await(30, TimeUnit.SECONDS);

    for (int c = 0; c < callCount; c++) {
      List<Reply> replies = results.get(c);
      assertNotNull("call " + c + " produced no response", replies);
      assertEquals("call " + c + " message count", perCall, replies.size());
      for (int i = 0; i < perCall; i++) {
        assertEquals("echo-c" + c + "-" + i, replies.get(i).getMessage());
      }
    }
  }

  @Test
  public void testServerStreamingBackpressure() throws Exception {
    int total = 300;

    AtomicInteger drains = new AtomicInteger();

    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      AtomicInteger written = new AtomicInteger();
      AtomicBoolean ended = new AtomicBoolean();
      Runnable[] pump = new Runnable[1];
      pump[0] = () -> {
        while (written.get() < total && !response.writeQueueFull()) {
          response.write(Reply.newBuilder().setMessage("m-" + written.getAndIncrement()).build());
        }
        if (written.get() >= total) {
          if (ended.compareAndSet(false, true)) {
            response.end();
          }
        } else {
          drains.incrementAndGet();
          response.drainHandler(v -> pump[0].run());
        }
      };
      pump[0].run();
    }));

    List<Reply> replies = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(20, TimeUnit.SECONDS);

    assertEquals(total, replies.size());
    for (int i = 0; i < total; i++) {
      assertEquals("m-" + i, replies.get(i).getMessage());
    }
    assertTrue("expected the producer to stall on the flow-control window at least once", drains.get() > 0);
  }

  @Test
  public void testBidiInterleavedBackpressure() throws Exception {
    int total = 300;
    AtomicInteger serverStalls = new AtomicInteger();

    server.callHandler(PIPE_SERVER, request -> {
      GrpcServerResponse<Request, Reply> response = request.response();
      request.handler(req -> {
        response.write(Reply.newBuilder().setMessage("echo-" + req.getName()).build());
        if (response.writeQueueFull()) {
          serverStalls.incrementAndGet();
          request.pause();
          response.drainHandler(v -> request.resume());
        }
      });
      request.endHandler(v -> response.end());
    });

    List<Reply> replies = client.request(PIPE_CLIENT)
      .compose(request -> {
        for (int i = 0; i < total; i++) {
          request.write(Request.newBuilder().setName("r-" + i).build());
        }
        request.end();
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(30, TimeUnit.SECONDS);

    assertEquals(total, replies.size());
    for (int i = 0; i < total; i++) {
      assertEquals("echo-r-" + i, replies.get(i).getMessage());
    }
    assertTrue("expected the server to stall its reader on the response window at least once", serverStalls.get() > 0);
  }

  @Test
  public void testCancelMidStream() throws Exception {
    int serverTotal = 30;
    AtomicInteger received = new AtomicInteger();
    AtomicInteger receivedAfterCancel = new AtomicInteger();
    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicBoolean serverNotified = new AtomicBoolean();
    AtomicInteger serverWrites = new AtomicInteger();
    Promise<Void> done = Promise.promise();

    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      long timer = vertx.setPeriodic(10, id -> {
        int i = serverWrites.getAndIncrement();
        if (i >= serverTotal) {
          vertx.cancelTimer(id);
          response.end();
        } else {
          response.write(Reply.newBuilder().setMessage("s-" + i).build());
        }
      });
      request.errorHandler(err -> {
        if (err == GrpcError.CANCELLED) {
          serverNotified.set(true);
        }
        vertx.cancelTimer(timer);
      });
    }));

    client.request(SOURCE_CLIENT).onSuccess(request -> {
      request.end(Empty.getDefaultInstance());
      request.response().onSuccess(response -> response.handler(item -> {
        if (cancelled.get()) {
          receivedAfterCancel.incrementAndGet();
        }
        if (received.incrementAndGet() == 3 && cancelled.compareAndSet(false, true)) {
          request.cancel();
          vertx.setTimer(500, t -> done.tryComplete());
        }
      }));
    });

    done.future().await(10, TimeUnit.SECONDS);

    assertEquals("cancel should have stopped delivery before the server finished", 0, receivedAfterCancel.get());
    assertEquals(3, received.get());
    assertTrue("client should not have received the full stream", received.get() < serverTotal);
    assertTrue("server should have been notified of the cancel", serverNotified.get());
    assertTrue("server should have stopped producing before the full stream", serverWrites.get() < serverTotal);
  }
}
