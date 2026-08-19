package io.vertx.grpc.eventbus.tests.eventbus;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.*;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.tests.TestConstants;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.eventbus.impl.EventBusHeaders;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.common.tests.*;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventBusGrpcStreamingTest extends EventBusGrpcTestBase {

  private EventBusGrpcServer server;
  private EventBusGrpcClient client;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    server = EventBusGrpcServer.server(vertx).await();
    client = EventBusGrpcClient.client(vertx).await();
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
  public void testQueuedWriteCompletesOnDrain() throws Exception {
    AtomicBoolean stalled = new AtomicBoolean();
    AtomicInteger written = new AtomicInteger();
    AtomicInteger drains = new AtomicInteger();
    Promise<Void> queued = Promise.promise();

    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      int cnt = 0;
      while (!response.writeQueueFull()) {
        response.write(Reply.newBuilder().setMessage("m-" + cnt++).build());
      }
      response.drainHandler(v -> drains.incrementAndGet());
      Future<Void> write = response.write(Reply.newBuilder().setMessage("queued").build());
      stalled.set(!write.isComplete());
      written.set(1 + cnt);
      write.onComplete(queued);
      response.end();
    }));


    List<Reply> replies = client.request(SOURCE_CLIENT)
      .compose(request -> {
        Future<List<Reply>> res = request.response().compose(EventBusGrpcStreamingTest::collect);
        request.end(Empty.getDefaultInstance());
        return res;
      })
      .await(20, TimeUnit.SECONDS);

    assertTrue("Expected to have more than one message: " + written.get(), written.get() > 1);
    assertEquals(written.get(), replies.size());
    assertTrue("the write must not complete while the message sits behind a closed window", stalled.get());
    queued.future().await(20, TimeUnit.SECONDS);
    assertEquals("queued", replies.get(replies.size() - 1).getMessage());
    assertEquals(1, drains.get());
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

  @Test
  public void testMultiplexAcrossClients() throws Exception {
    server.callHandler(PIPE_SERVER, request -> {
      request.handler(req -> request.response().write(Reply.newBuilder().setMessage("echo-" + req.getName()).build()));
      request.endHandler(v -> request.response().end());
    });

    EventBusGrpcClient clientA = EventBusGrpcClient.client(vertx).await();
    EventBusGrpcClient clientB = EventBusGrpcClient.client(vertx).await();

    Future<List<Reply>> a = clientA.request(PIPE_CLIENT).compose(request -> {
      request.write(Request.newBuilder().setName("a1").build());
      request.end(Request.newBuilder().setName("a2").build());
      return request.response();
    }).compose(EventBusGrpcStreamingTest::collect);

    Future<List<Reply>> b = clientB.request(PIPE_CLIENT).compose(request -> {
      request.write(Request.newBuilder().setName("b1").build());
      request.end(Request.newBuilder().setName("b2").build());
      return request.response();
    }).compose(EventBusGrpcStreamingTest::collect);

    Future.all(a, b).await(10, TimeUnit.SECONDS);

    // The server multiplexes both clients' streams over its single private consumer, demuxed by
    // server-assigned stream ids, so the two streams must not cross-talk.
    List<Reply> ra = a.result();
    assertEquals(2, ra.size());
    assertEquals("echo-a1", ra.get(0).getMessage());
    assertEquals("echo-a2", ra.get(1).getMessage());
    List<Reply> rb = b.result();
    assertEquals(2, rb.size());
    assertEquals("echo-b1", rb.get(0).getMessage());
    assertEquals("echo-b2", rb.get(1).getMessage());
  }

  @Test
  public void testServerCloseTerminatesStream() throws Exception {
    Promise<Void> serverReady = Promise.promise();
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      request.response().write(Reply.newBuilder().setMessage("first").build());
    }));

    Promise<Throwable> clientFailed = Promise.promise();
    client.request(SOURCE_CLIENT).onSuccess(request -> {
      request.end(Empty.getDefaultInstance());
      request.response().onSuccess(response -> {
        response.exceptionHandler(clientFailed::tryComplete);
        serverReady.tryComplete();
      });
    });

    // Wait until the stream is live, then close the server: its in-flight streams must be terminated
    // and the client notified, rather than left hanging.
    serverReady.future().await(10, TimeUnit.SECONDS);

    server.close().await(10, TimeUnit.SECONDS);

    Throwable failure = clientFailed.future().await(10, TimeUnit.SECONDS);
    assertNotNull("client should have been notified the stream was terminated", failure);
  }

  @Test
  public void testClientWriteFailureFailsStream() throws Exception {
    server.callHandler(SINK_SERVER, request -> {
      request.handler(req -> {
      });
      request.endHandler(v -> request.response().end(Empty.getDefaultInstance()));
    });

    // Simulate the server node leaving the cluster: drop its consumer as soon as the first client
    // message is on the wire, so the following writes have no consumer to deliver to.
    vertx.eventBus().addOutboundInterceptor(dc -> {
      if (dc.message().address().startsWith("grpc.eb.server.") && dc.message().body() instanceof Buffer) {
        JsonObject json = ((Buffer) dc.message().body()).toJsonObject();
        if (json.getJsonObject("message") != null) {
          server.close().onComplete(ar -> dc.next());
          return;
        }
      }
      dc.next();
    });

    GrpcClientRequest<Request, Empty> request = client.request(SINK_CLIENT).await(10, TimeUnit.SECONDS);
    request.format(WireFormat.JSON);
    Future<GrpcClientResponse<Request, Empty>> response = request.response();

    try {
      request.write(Request.newBuilder().setName("a").build()).await(10, TimeUnit.SECONDS);
      fail("the write must fail once the server address has no consumer");
    } catch (ReplyException e) {
      assertEquals(ReplyFailure.NO_HANDLERS, e.failureType());
    }

    // The stream is given up on rather than left hanging: the response is failed too.
    try {
      response.await(10, TimeUnit.SECONDS);
      fail("the response must be failed when the stream is given up");
    } catch (Exception expected) {
    }
  }

  @Test
  public void testServerWriteFailureFailsStream() throws Exception {
    Promise<Throwable> serverFailed = Promise.promise();
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      response.exceptionHandler(serverFailed::tryComplete);
      for (int i = 0; i < 50; i++) {
        response.write(Reply.newBuilder().setMessage("x-" + i).build());
      }
      response.end();
    }));

    // Simulate the client node leaving the cluster: drop its consumer as soon as the first server
    // message is on the wire, so the following writes have no consumer to deliver to.
    vertx.eventBus().addOutboundInterceptor(dc -> {
      if (dc.message().address().startsWith("grpc.eb.client.") && dc.message().body() instanceof Buffer) {
        JsonObject json = ((Buffer) dc.message().body()).toJsonObject();
        if (json.getJsonObject("message") != null) {
          client.close().onComplete(ar -> dc.next());
          return;
        }
      }
      dc.next();
    });

    client.request(SOURCE_CLIENT).onSuccess(request -> {
      request.format(WireFormat.JSON);
      request.end(Empty.getDefaultInstance());
    });

    Throwable failure = serverFailed.future().await(10, TimeUnit.SECONDS);
    assertNotNull("the service handler must be notified when the client address has no consumer", failure);
  }

  @Test
  public void testServerEndCompletesWhenTrailersAreSent() throws Exception {
    Promise<Void> ended = Promise.promise();
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      response.write(Reply.newBuilder().setMessage("first").build());
      response.end().onComplete(ended);
    }));

    List<Reply> replies = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(10, TimeUnit.SECONDS);

    // end() completes only once the trailers have actually been written to the event bus
    ended.future().await(10, TimeUnit.SECONDS);
    assertEquals(1, replies.size());
    assertEquals("first", replies.get(0).getMessage());
  }

  @Test
  public void testServerEndFailsWhenTrailersCannotBeWritten() throws Exception {
    Promise<Throwable> endFailed = Promise.promise();
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      response.exceptionHandler(err -> {
      });
      response.write(Reply.newBuilder().setMessage("first").build());
      response.end().onFailure(endFailed::tryComplete);
    }));

    // Simulate the client node leaving the cluster once the first message is on the wire, so the
    // trailers that follow have no consumer to deliver to.
    vertx.eventBus().addOutboundInterceptor(dc -> {
      if (dc.message().address().startsWith("grpc.eb.client.") && dc.message().body() instanceof Buffer) {
        JsonObject json = ((Buffer) dc.message().body()).toJsonObject();
        if (json.getJsonObject("message") != null) {
          client.close().onComplete(ar -> dc.next());
          return;
        }
      }
      dc.next();
    });

    client.request(SOURCE_CLIENT).onSuccess(request -> {
      request.format(WireFormat.JSON);
      request.end(Empty.getDefaultInstance());
    });

    Throwable failure = endFailed.future().await(10, TimeUnit.SECONDS);
    assertNotNull("end() must report the outcome of the trailers write", failure);
  }

  @Test
  public void testLivenessIsOnByDefault() throws Exception {
    assertFalse(new EventBusGrpcClientOptions().getPingInterval().isZero());
    assertTrue(new EventBusGrpcClientOptions().getPingTimeout().compareTo(new EventBusGrpcClientOptions().getPingInterval()) > 0);
    assertFalse(new EventBusGrpcServerOptions().getMaxPingTimeout().isZero());

    for (Duration disabled : new Duration[]{Duration.ZERO, Duration.ofMillis(-1)}) {
      try {
        new EventBusGrpcClientOptions().setPingInterval(disabled);
        fail("pingInterval " + disabled + " must be rejected");
      } catch (IllegalArgumentException expected) {
      }
      try {
        new EventBusGrpcClientOptions().setPingTimeout(disabled);
        fail("pingTimeout " + disabled + " must be rejected");
      } catch (IllegalArgumentException expected) {
      }
      try {
        new EventBusGrpcServerOptions().setMaxPingTimeout(disabled);
        fail("maxPingTimeout " + disabled + " must be rejected");
      } catch (IllegalArgumentException expected) {
      }
    }

    // A timeout that expires before the next ping is due would declare every peer down on the first check.
    try {
      EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions().setPingInterval(Duration.ofSeconds(1)).setPingTimeout(Duration.ofSeconds(1)));
      fail("a ping timeout that is not greater than the interval must be rejected");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testServerGivesUpPeerThatNeverAdvertisedAPingTimeout() throws Exception {
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions().setMaxPingTimeout(Duration.ofMillis(150))).await(10, TimeUnit.SECONDS);

    Promise<Throwable> serverFailed = Promise.promise();
    server.callHandler(SINK_SERVER, request -> {
      request.response().exceptionHandler(serverFailed::tryComplete);
      request.handler(req -> {
      });
      request.endHandler(v -> request.response().end(Empty.getDefaultInstance()));
    });

    // Open the stream by hand, omitting the advertisement a real client would send.
    DeliveryOptions handshake = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Sink")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name())
      .addHeader(EventBusHeaders.CLIENT_ADDRESS, "grpc.eb.client.silent")
      .addHeader(EventBusHeaders.STREAM_ID, "1");
    vertx.eventBus().consumer("grpc.eb.client.silent", msg -> {
    }).completion().await(10, TimeUnit.SECONDS);
    vertx.eventBus().request(TestConstants.TEST_SERVICE.fullyQualifiedName(), Buffer.buffer(), handshake).await(10, TimeUnit.SECONDS);

    Throwable failure = serverFailed.future().await(10, TimeUnit.SECONDS);
    assertNotNull("a peer that never advertised must still be given up on", failure);
  }

  @Test
  public void testClientGivesStreamUpWhenServerStopsAcking() throws Exception {
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()).await(10, TimeUnit.SECONDS);
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()
      .setWireFormat(WireFormat.JSON)
      .setPingInterval(Duration.ofMillis(100))
      .setPingTimeout(Duration.ofMillis(400))).await(10, TimeUnit.SECONDS);

    Promise<Throwable> serverCancelled = Promise.promise();
    server.callHandler(SOURCE_SERVER, request -> {
      request.response().exceptionHandler(serverCancelled::tryComplete);
      request.handler(empty -> request.response().write(Reply.newBuilder().setMessage("first").build()));
    });

    vertx.eventBus().addOutboundInterceptor(dc -> {
      if (dc.message().address().startsWith("grpc.eb.client.") && dc.message().body() instanceof Buffer) {
        JsonObject ping = ((Buffer) dc.message().body()).toJsonObject().getJsonObject("ping");
        if (ping != null && ping.getBoolean("ack", false)) {
          return;
        }
      }
      dc.next();
    });

    Promise<Throwable> failed = Promise.promise();
    client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .onSuccess(response -> {
        response.handler(reply -> {
        });
        response.exceptionHandler(failed::tryComplete);
      })
      .onFailure(failed::tryFail);

    Throwable failure = failed.future().await(10, TimeUnit.SECONDS);
    assertNotNull("the client must give the stream up once the server stops acking", failure);
    assertTrue("expected the unanswered ping to be the cause, got " + failure, failure instanceof java.util.concurrent.TimeoutException);

    Throwable serverFailure = serverCancelled.future().await(10, TimeUnit.SECONDS);
    assertNotNull("the still-alive server must be cancelled by the client's give-up", serverFailure);
  }

  @Test
  public void testPingKeepsIdleStreamAlive() throws Exception {
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()).await(10, TimeUnit.SECONDS);
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions().setPingInterval(Duration.ofMillis(100))).await(10, TimeUnit.SECONDS);

    Promise<Throwable> serverFailed = Promise.promise();
    server.callHandler(SOURCE_SERVER, request -> {
      request.response().exceptionHandler(serverFailed::tryComplete);
      request.handler(empty -> request.response().write(Reply.newBuilder().setMessage("first").build()));
    });

    Promise<Throwable> failed = Promise.promise();
    AtomicInteger received = new AtomicInteger();
    client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .onSuccess(response -> {
        response.handler(reply -> received.incrementAndGet());
        response.exceptionHandler(failed::tryComplete);
      })
      .onFailure(failed::tryFail);

    try {
      failed.future().await(600, TimeUnit.MILLISECONDS);
      fail("the stream must not be given up while the peers are exchanging pings");
    } catch (Exception noFailureWithinWindow) {
      // expected: the await times out because the stream stayed alive
    }

    assertFalse("the server must not give the stream up while the client is pinging", serverFailed.future().isComplete());
    assertEquals(1, received.get());
  }

  @Test
  public void testServerGivesStreamUpWhenClientStopsPinging() throws Exception {
    // The client asks for far longer than the server honours, so the server holds it to maxPingTimeout and
    // gives the stream up while the client is still waiting on its own deadline.
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()
      .setMaxPingTimeout(Duration.ofMillis(500))).await(10, TimeUnit.SECONDS);
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()
      .setWireFormat(WireFormat.JSON)
      .setPingInterval(Duration.ofMillis(100))
      .setPingTimeout(Duration.ofSeconds(30))).await(10, TimeUnit.SECONDS);

    Promise<Throwable> serverFailed = Promise.promise();
    server.callHandler(SINK_SERVER, request -> {
      request.response().exceptionHandler(serverFailed::tryComplete);
      request.handler(req -> {
      });
      request.endHandler(v -> request.response().end(Empty.getDefaultInstance()));
    });

    vertx.eventBus().addInboundInterceptor(dc -> {
      if (dc.message().address().startsWith("grpc.eb.server.") && dc.message().body() instanceof Buffer) {
        if (((Buffer) dc.message().body()).toJsonObject().getJsonObject("ping") != null) {
          return;
        }
      }
      dc.next();
    });

    Promise<Throwable> clientCancelled = Promise.promise();
    client.request(SINK_CLIENT).onSuccess(request -> {
      request.response().onFailure(clientCancelled::tryComplete);
      request.write(Request.newBuilder().setName("a").build());
    });

    Throwable failure = serverFailed.future().await(10, TimeUnit.SECONDS);
    assertNotNull("the server must give the stream up once the client stops pinging", failure);
    Throwable clientFailure = clientCancelled.future().await(10, TimeUnit.SECONDS);
    assertNotNull("the still-alive client must be cancelled by the server's give-up", clientFailure);
  }

  @Test
  public void testBothSidesGiveTheStreamUpAtTheSameDeadline() throws Exception {
    Duration pingInterval = Duration.ofMillis(200);
    Duration pingTimeout = Duration.ofSeconds(2);
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()).await(10, TimeUnit.SECONDS);
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()
      .setWireFormat(WireFormat.JSON)
      .setPingInterval(pingInterval)
      .setPingTimeout(pingTimeout)).await(10, TimeUnit.SECONDS);

    AtomicLong serverFailedAt = new AtomicLong();
    Promise<Throwable> serverFailed = Promise.promise();
    server.callHandler(SINK_SERVER, request -> {
      request.response().exceptionHandler(err -> {
        serverFailedAt.compareAndSet(0, System.currentTimeMillis());
        serverFailed.tryComplete(err);
      });
      request.handler(req -> {
      });
      request.endHandler(v -> request.response().end(Empty.getDefaultInstance()));
    });

    // Let the peers ping, then partition them: every frame between the two private addresses is dropped,
    // so neither side hears the other and neither can be told by the other that the stream is gone. Each
    // side starts its countdown at the last ping it saw, and the cut can land between a ping and its ack,
    // so the two references are taken apart.
    AtomicLong lastPingToServer = new AtomicLong();
    AtomicLong lastAckToClient = new AtomicLong();
    AtomicBoolean partitioned = new AtomicBoolean();
    Promise<Void> pinged = Promise.promise();
    vertx.eventBus().addInboundInterceptor(dc -> {
      String address = dc.message().address();
      boolean toServer = address.startsWith("grpc.eb.server.");
      if (toServer || address.startsWith("grpc.eb.client.")) {
        if (partitioned.get()) {
          return;
        }
        if (dc.message().body() instanceof Buffer && ((Buffer) dc.message().body()).toJsonObject().getJsonObject("ping") != null) {
          if (toServer) {
            lastPingToServer.set(System.currentTimeMillis());
          } else {
            lastAckToClient.set(System.currentTimeMillis());
            pinged.tryComplete();
          }
        }
      }
      dc.next();
    });

    AtomicLong clientFailedAt = new AtomicLong();
    Promise<Throwable> clientFailed = Promise.promise();
    client.request(SINK_CLIENT).onSuccess(request -> {
      request.response().onFailure(err -> {
        clientFailedAt.compareAndSet(0, System.currentTimeMillis());
        clientFailed.tryComplete(err);
      });
      request.write(Request.newBuilder().setName("a").build());
    });

    pinged.future().await(10, TimeUnit.SECONDS);
    partitioned.set(true);

    assertNotNull("the server must give the stream up once it stops hearing the client", serverFailed.future().await(10, TimeUnit.SECONDS));
    assertNotNull("the client must give the stream up once it stops hearing the server", clientFailed.future().await(10, TimeUnit.SECONDS));

    // Both sides hold the stream for the timeout the client advertised, rather than one of them deriving
    // a shorter deadline of its own and killing a stream the other is still riding out. Detection lands
    // on a liveness check, so the upper bound carries that period: the client checks every ping interval,
    // the server every half timeout.
    long floor = pingTimeout.toMillis();
    long ceiling = pingTimeout.toMillis() + pingTimeout.toMillis() / 2 + pingInterval.toMillis() * 5;
    long serverElapsed = serverFailedAt.get() - lastPingToServer.get();
    long clientElapsed = clientFailedAt.get() - lastAckToClient.get();
    assertTrue("the server gave the stream up after " + serverElapsed + " ms, before the " + floor + " ms deadline", serverElapsed >= floor);
    assertTrue("the client gave the stream up after " + clientElapsed + " ms, before the " + floor + " ms deadline", clientElapsed >= floor);
    assertTrue("the server took " + serverElapsed + " ms to give the stream up, more than " + ceiling + " ms", serverElapsed <= ceiling);
    assertTrue("the client took " + clientElapsed + " ms to give the stream up, more than " + ceiling + " ms", clientElapsed <= ceiling);
  }

  @Test
  public void testCongestionShorterThanTheTimeoutKeepsTheStream() throws Exception {
    Duration pingInterval = Duration.ofMillis(200);
    Duration pingTimeout = Duration.ofSeconds(2);
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()).await(10, TimeUnit.SECONDS);
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()
      .setWireFormat(WireFormat.JSON)
      .setPingInterval(pingInterval)
      .setPingTimeout(pingTimeout)).await(10, TimeUnit.SECONDS);

    Promise<Throwable> serverFailed = Promise.promise();
    server.callHandler(SINK_SERVER, request -> {
      request.response().exceptionHandler(serverFailed::tryComplete);
      request.handler(req -> {
      });
      request.endHandler(v -> request.response().end(Empty.getDefaultInstance()));
    });

    AtomicBoolean congested = new AtomicBoolean();
    Promise<Void> acked = Promise.promise();
    vertx.eventBus().addInboundInterceptor(dc -> {
      String address = dc.message().address();
      if (address.startsWith("grpc.eb.server.") || address.startsWith("grpc.eb.client.")) {
        if (congested.get()) {
          return;
        }
        if (address.startsWith("grpc.eb.client.") && dc.message().body() instanceof Buffer
          && ((Buffer) dc.message().body()).toJsonObject().getJsonObject("ping") != null) {
          acked.tryComplete();
        }
      }
      dc.next();
    });

    Promise<Throwable> clientFailed = Promise.promise();
    GrpcClientRequest<Request, Empty> request = client.request(SINK_CLIENT).await(10, TimeUnit.SECONDS);
    request.response().onFailure(clientFailed::tryComplete);
    request.write(Request.newBuilder().setName("a").build());
    acked.future().await(10, TimeUnit.SECONDS);

    // Nothing gets through for less than the ping timeout. Neither side may take that for a peer that is
    // gone, however many pings it costs, and the stream must still work once the traffic flows again.
    congested.set(true);
    Promise<Void> healed = Promise.promise();
    vertx.setTimer(pingTimeout.toMillis() / 2, id -> {
      congested.set(false);
      healed.complete();
    });
    healed.future().await(10, TimeUnit.SECONDS);

    assertFalse("the server must ride the congestion out", serverFailed.future().isComplete());
    assertFalse("the client must ride the congestion out", clientFailed.future().isComplete());

    request.end();
    request.response().compose(GrpcReadStream::last).await(10, TimeUnit.SECONDS);
  }

  @Test
  public void testPingIsSentPerPeerNotPerStream() throws Exception {
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions()).await(10, TimeUnit.SECONDS);
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()
      .setWireFormat(WireFormat.JSON)
      .setPingInterval(Duration.ofMillis(100))).await(10, TimeUnit.SECONDS);

    server.callHandler(SOURCE_SERVER, request -> request.handler(empty ->
      request.response().write(Reply.newBuilder().setMessage("first").build())));

    AtomicInteger probes = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(dc -> {
      if (dc.message().address().startsWith("grpc.eb.server.") && dc.message().body() instanceof Buffer) {
        if (((Buffer) dc.message().body()).toJsonObject().getJsonObject("ping") != null) {
          probes.incrementAndGet();
        }
      }
      dc.next();
    });

    // The service never ends these, so all four stay open and bound to the same peer.
    List<Future<?>> opened = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      opened.add(client.request(SOURCE_CLIENT).compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response().onSuccess(response -> response.handler(reply -> {
        }));
      }));
    }

    for (Future<?> stream : opened) {
      stream.await(10, TimeUnit.SECONDS);
    }

    int before = probes.get();
    Promise<Void> settled = Promise.promise();
    vertx.setTimer(450, id -> settled.complete());
    settled.future().await(10, TimeUnit.SECONDS);

    int sent = probes.get() - before;
    assertTrue("the peer must actually be probed, got " + sent, sent >= 2);
    assertTrue("expected about one probe per interval for the whole peer, got " + sent, sent <= 8);
  }

  @Test
  public void testNoHeadOfLineBlocking() throws Exception {
    int count = 200;
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> {
      for (int i = 0; i < count; i++) {
        request.response().write(Reply.newBuilder().setMessage("x-" + i).build());
      }
      request.response().end();
    }));

    // Open a stream and pause its reader without resuming, so it stalls on its window once the
    // initial credit is spent. It shares the client's single private consumer with the next stream.
    GrpcReadStream<Reply> stalled = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .await(10, TimeUnit.SECONDS);
    stalled.pause();

    // A second stream over the same consumer must still run to completion; a stalled stream must
    // never pause the shared consumer and block the others.
    List<Reply> replies = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(10, TimeUnit.SECONDS);

    assertEquals(count, replies.size());
    assertEquals("x-0", replies.get(0).getMessage());
    assertEquals("x-" + (count - 1), replies.get(count - 1).getMessage());
  }

  @Test
  public void testInitialFailureSurfacesError() throws Exception {
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> request.response().end()));

    try {
      client.request(UNKNOWN_CLIENT)
        .compose(request -> {
          request.end(Empty.getDefaultInstance());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(10, TimeUnit.SECONDS);
      fail("Should have thrown");
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.UNIMPLEMENTED, e.actualStatus());
    }
  }

  @Test
  public void testMalformedHandshakeReplyFailsFast() throws Exception {
    String fqn = PIPE_SERVER.serviceName().fullyQualifiedName();
    vertx.eventBus().<Buffer>consumer(fqn, msg -> msg.reply(Buffer.buffer(), new DeliveryOptions()
      .addHeader(EventBusHeaders.SERVER_ADDRESS, "s.addr"))).completion().await(5, TimeUnit.SECONDS);
    try {
      client.request(PIPE_CLIENT)
        .compose(request -> {
          request.write(Request.getDefaultInstance());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(5, TimeUnit.SECONDS);
      fail("a malformed handshake reply should fail the call");
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INTERNAL, e.actualStatus());
    }
  }

  @Test
  public void testInvalidWireFormatIsRejected() throws Exception {
    server.callHandler(SOURCE_SERVER, request -> request.handler(empty -> request.response().end()));

    DeliveryOptions options = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Source")
      .addHeader(EventBusHeaders.WIRE_FORMAT, "NOT_A_FORMAT")
      .addHeader(EventBusHeaders.CLIENT_ADDRESS, "c.addr")
      .addHeader(EventBusHeaders.STREAM_ID, "1")
      .setSendTimeout(3000);
    try {
      vertx.eventBus().request(SOURCE_SERVER.serviceName().fullyQualifiedName(), Buffer.buffer(), options)
        .await(5, TimeUnit.SECONDS);
      fail("an invalid wire format should be rejected");
    } catch (ReplyException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT.code, e.failureCode());
    }
  }

  @Test
  public void testJsonWireFormatEncodesFramesAsJson() throws Exception {
    server.callHandler(PIPE_SERVER, request -> {
      request.handler(req -> request.response().write(Reply.newBuilder().setMessage("echo-" + req.getName()).build()));
      request.endHandler(v -> request.response().end());
    });

    List<JsonObject> frames = new CopyOnWriteArrayList<>();
    vertx.eventBus().addOutboundInterceptor(ctx -> {
      Object body = ctx.message().body();
      if (ctx.message().address().startsWith("grpc.eb.") && body instanceof Buffer && ((Buffer) body).length() > 0) {
        frames.add(new JsonObject((Buffer) body));
      }
      ctx.next();
    });

    List<Reply> replies = client.request(PIPE_CLIENT)
      .compose(request -> {
        request.format(WireFormat.JSON);
        request.write(Request.newBuilder().setName("a").build());
        request.end(Request.newBuilder().setName("b").build());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(10, TimeUnit.SECONDS);

    assertEquals(2, replies.size());
    assertEquals("echo-a", replies.get(0).getMessage());
    assertFalse("expected JSON transport frames on the bus", frames.isEmpty());
    assertTrue("a message frame should carry a JSON message object", frames.stream().anyMatch(f -> f.containsKey("message")));
  }

  @Test
  public void testClientDefaultWireFormatAppliesWithoutOverride() throws Exception {
    EventBusGrpcClient jsonClient = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions().setWireFormat(WireFormat.JSON)).await();
    server.callHandler(PIPE_SERVER, request -> {
      request.handler(req -> request.response().write(Reply.newBuilder().setMessage("echo-" + req.getName()).build()));
      request.endHandler(v -> request.response().end());
    });

    List<JsonObject> frames = new CopyOnWriteArrayList<>();
    vertx.eventBus().addOutboundInterceptor(ctx -> {
      Object body = ctx.message().body();
      if (ctx.message().address().startsWith("grpc.eb.") && body instanceof Buffer && ((Buffer) body).length() > 0) {
        frames.add(new JsonObject((Buffer) body));
      }
      ctx.next();
    });

    List<Reply> replies = jsonClient.request(PIPE_CLIENT)
      .compose(request -> {
        request.write(Request.newBuilder().setName("a").build());
        request.end(Request.newBuilder().setName("b").build());
        return request.response();
      })
      .compose(EventBusGrpcStreamingTest::collect)
      .await(10, TimeUnit.SECONDS);

    assertEquals(2, replies.size());
    assertFalse("the client's default wire format should produce JSON frames without request.format()", frames.isEmpty());
    assertTrue(frames.stream().anyMatch(f -> f.containsKey("message")));
  }

  @Test
  public void testUnsupportedWireFormatRejected() throws Exception {
    EventBusGrpcServer protobufOnly = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions().setSupportedWireFormats(Collections.singleton(WireFormat.PROTOBUF))).await();
    protobufOnly.callHandler(PIPE_SERVER, request -> {
      request.handler(req -> request.response().write(Reply.newBuilder().setMessage("echo-" + req.getName()).build()));
      request.endHandler(v -> request.response().end());
    });

    try {
      client.request(PIPE_CLIENT)
        .compose(request -> {
          request.format(WireFormat.JSON);
          request.write(Request.newBuilder().setName("a").build());
          request.end(Request.newBuilder().setName("b").build());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(10, TimeUnit.SECONDS);
      fail("a JSON request to a PROTOBUF-only server should be rejected");
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.UNIMPLEMENTED, e.actualStatus());
    }
  }
}
