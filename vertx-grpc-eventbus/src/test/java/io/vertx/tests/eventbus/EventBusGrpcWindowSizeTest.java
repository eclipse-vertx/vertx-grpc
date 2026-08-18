package io.vertx.tests.eventbus;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.tests.common.grpc.Empty;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class EventBusGrpcWindowSizeTest extends EventBusGrpcTestBase {

  EventBusGrpcServer server;
  EventBusGrpcClient client;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);
    client = EventBusGrpcClient.client(vertx, new EventBusGrpcClientOptions()).await();
    server = EventBusGrpcServer.server(vertx, new EventBusGrpcServerOptions().setInitialWindowSize(8)).await();
  }

  @Test
  public void testFlowControl(TestContext should) {

    Async async1 = should.async();

    Promise<Void> signal = Promise.promise();

    server.callHandler(PIPE_SERVER, new Handler<>() {

      int submitted = 0;
      int written = 0;

      @Override
      public void handle(GrpcServerRequest<Request, Reply> request) {
        GrpcServerResponse<Request, Reply> response = request.response();
        request.handler(item -> {
          response.writeHead();
        });
        request.endHandler(v -> {
          Future<Void> last;
          do {
            int val = ++submitted;
            Future<Void> write = response.write(Reply.newBuilder().setMessage("msg-" + val).build());
            last = write;
            write.onComplete(should.asyncAssertSuccess(v2 -> {
              written++;
              // Default window size
              if (val == 64) {
                vertx.setTimer(100, id -> {
                  // Check we haven't written extra messages to the wire
                  should.assertEquals(64, written);
                  // Signal client to consume messages
                  signal.succeed();
                });
              }
            }));
          }
          while (!response.writeQueueFull());
          AtomicInteger drains = new AtomicInteger();
          response.drainHandler(v2 -> {
            drains.incrementAndGet();
          });
          last.onComplete(ar -> {
            should.assertEquals(1, drains.incrementAndGet());
            async1.complete();
          });
        });
      }
    });

    Async async2 = should.async();

    Future<GrpcClientRequest<Request, Reply>> fut = client.request(PIPE_CLIENT);
    fut.onComplete(should.asyncAssertSuccess(request -> {
      request.write(Request.getDefaultInstance());
      request
        .response()
        .onComplete(should.asyncAssertSuccess(response -> {
          response.handler(msg -> {
            should.fail();
          });
          response.endHandler(msg -> {
            should.fail();
          });
          AtomicInteger consumed = new AtomicInteger();
          signal.future().onComplete(should.asyncAssertSuccess(pending -> {
            // Consume 1/2 of the window size to trigger a window update frame
            response.handler(msg -> {
              if (consumed.incrementAndGet() == 32) {
                vertx.setTimer(100, id -> {
                  should.assertEquals(32, consumed.get());
                  async2.complete();
                });
              }
            });
            response.fetch(32);
          }));
          response.pause();
          request.end();
      }));
    }));
  }

  @Test
  public void testServerWindowSizeClientInitialValue(TestContext should) {

    Async async = should.async();

    server.callHandler(PIPE_SERVER, request -> {
      request.pause();
      request
        .response()
        .writeHead();
    });

    Future<GrpcClientRequest<Request, Reply>> fut = client.request(PIPE_CLIENT);
    fut.onComplete(should.asyncAssertSuccess(request -> {
      should.assertFalse(request.writeQueueFull());
      request.drainHandler(v -> {
        int written = 0;
        while (!request.writeQueueFull()) {
          request.write(Request.getDefaultInstance());
          written++;
        }
        // No message can be really written, so we consume the outbound message queue count
        should.assertEquals(16, written);
        async.complete();
      });
      request.writeHead();
    }));
  }

  @Test
  public void testServerWindowSizeClientAdjustment(TestContext should) {

    Async async2 = should.async();

    server.callHandler(PIPE_SERVER, request -> {
      request.pause();
      request
        .response()
        .writeHead();
    });

    Future<GrpcClientRequest<Request, Reply>> fut = client.request(PIPE_CLIENT);
    fut.onComplete(should.asyncAssertSuccess(request -> {
      request.write(Request.getDefaultInstance());
      request
        .response()
        .onComplete(should.asyncAssertSuccess(response -> {
          int written = 1;
          while (!request.writeQueueFull()) {
            request.write(Request.getDefaultInstance());
            written++;
          }
          // 8: server window size
          // 16: outbound queue high watermark
          should.assertEquals(written, 8 + 16);
          async2.complete();
        }));
    }));
  }

  @Test
  public void testReplenishWindow(TestContext should) {

    Async async2 = should.async();

    AtomicInteger numberOfWindowUpdates = new AtomicInteger();

    vertx.eventBus().addOutboundInterceptor(ctx -> {
      if (ctx.message().headers().contains("grpc-wire-format", "json", true)) {
        JsonObject json = new JsonObject(ctx.body().toString());
        if (json.containsKey("windowUpdate")) {
          numberOfWindowUpdates.incrementAndGet();
        }
      }
      ctx.next();
    });

    server.callHandler(SOURCE_SERVER, request -> {
      GrpcServerResponse<Empty, Reply> response = request.response();
      int idx = 0;
      while (!response.writeQueueFull()) {
        response.write(Reply.newBuilder().setMessage("msg-" + (idx++)).build());
      }
      response.end();
    });

    Future<GrpcClientRequest<Empty, Reply>> fut = client.request(SOURCE_CLIENT);
    fut.onComplete(should.asyncAssertSuccess(request -> {
      request.format(WireFormat.JSON);
      request.end(Empty.getDefaultInstance());
      request
        .response()
        .onComplete(should.asyncAssertSuccess(response -> {
          AtomicInteger count = new AtomicInteger();
          response.handler(msg -> count.incrementAndGet());
          response.endHandler(v -> {
            int halfWindow = 32; // initialWindowSize / 2
            int replenishCycle = halfWindow + 1; // 33
            int expectedNumberOfWindowUpdates = 0;
            int val = count.get();
            while (val > halfWindow) {
              val -= replenishCycle;
              expectedNumberOfWindowUpdates++;
            }
            should.assertEquals(expectedNumberOfWindowUpdates, numberOfWindowUpdates.get());
            async2.complete();
          });
        }));
    }));
  }
}
