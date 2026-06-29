package io.vertx.tests.eventbus;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.*;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.eventbus.impl.EventBusHeaders;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.*;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventBusGrpcStreamingTest extends GrpcTestBase {

  private static final ServiceMethod<Empty, Reply> SOURCE_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Source", false, true, TestConstants.REPLY_ENC, TestConstants.EMPTY_DEC);
  private static final ServiceMethod<Request, Empty> SINK_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Sink", true, false, TestConstants.EMPTY_ENC, TestConstants.REQUEST_DEC);
  private static final ServiceMethod<Request, Reply> PIPE_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Pipe", true, true, TestConstants.REPLY_ENC, TestConstants.REQUEST_DEC);

  private static final ServiceMethod<Reply, Empty> SOURCE_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Source", false, true, TestConstants.EMPTY_ENC, TestConstants.REPLY_DEC);
  private static final ServiceMethod<Empty, Request> SINK_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Sink", true, false, TestConstants.REQUEST_ENC, TestConstants.EMPTY_DEC);
  private static final ServiceMethod<Reply, Request> PIPE_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Pipe", true, true, TestConstants.REQUEST_ENC, TestConstants.REPLY_DEC);

  private static final ServiceMethod<Reply, Empty> UNKNOWN_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Unknown", false, true, TestConstants.EMPTY_ENC, TestConstants.REPLY_DEC);

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
      serverReady.tryComplete();
    }));

    Promise<Throwable> clientFailed = Promise.promise();
    client.request(SOURCE_CLIENT).onSuccess(request -> {
      request.end(Empty.getDefaultInstance());
      request.response().onSuccess(response -> response.exceptionHandler(clientFailed::tryComplete));
    });

    // Wait until the stream is live, then close the server: its in-flight streams must be terminated
    // and the client notified, rather than left hanging.
    serverReady.future().await(10, TimeUnit.SECONDS);

    Promise<Void> closed = Promise.promise();
    server.close(closed);
    closed.future().await(10, TimeUnit.SECONDS);

    Throwable failure = clientFailed.future().await(10, TimeUnit.SECONDS);
    assertNotNull("client should have been notified the stream was terminated", failure);
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
    String fqn = SOURCE_SERVER.serviceName().fullyQualifiedName();
    vertx.eventBus().<Buffer>consumer(fqn, msg -> msg.reply(Buffer.buffer(), new DeliveryOptions()
      .addHeader(EventBusHeaders.SERVER_ADDRESS, "s.addr")
      .addHeader(EventBusHeaders.INITIAL_WINDOW, "64"))).completion().await(5, TimeUnit.SECONDS);

    try {
      client.request(SOURCE_CLIENT)
        .compose(request -> {
          request.end(Empty.getDefaultInstance());
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
      .addHeader(EventBusHeaders.CLIENT_STREAM_ID, "1")
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
