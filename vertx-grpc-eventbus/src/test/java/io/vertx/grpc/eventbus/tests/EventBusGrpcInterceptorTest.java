package io.vertx.grpc.eventbus.tests;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.tests.Empty;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EventBusGrpcInterceptorTest extends EventBusGrpcTestBase {

  private EventBusGrpcServer server;
  private EventBusGrpcClient client;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    server = EventBusGrpcServer.server(vertx).await();
    client = EventBusGrpcClient.client(vertx).await();
  }

  @Test
  public void testUnary(TestContext should) throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      public Result onClientConnect(String clientAddress, String streamId) {
        sequence.compareAndSet(0, 1);
        return super.onClientConnect(clientAddress, streamId);
      }
      @Override
      public Result onClientHeaders(String clientAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(1, 2);
        return super.onClientHeaders(clientAddress, streamId, headers);
      }
      @Override
      public Result onClientMessage(String clientAddress, WireFormat wireFormat, String streamId, Buffer msg) {
        sequence.compareAndSet(2, 3);
        return super.onClientMessage(clientAddress, wireFormat, streamId, msg);
      }
      @Override
      public Result onClientHalfClose(String clientAddress, String streamId) {
        sequence.compareAndSet(3, 4);
        return super.onClientHalfClose(clientAddress, streamId);
      }
      @Override
      public Result onServerConnect(String serverAddress, String streamId) {
        sequence.compareAndSet(4, 5);
        return super.onServerConnect(serverAddress, streamId);
      }
      @Override
      public Result onServerHeaders(String serverAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(5, 6);
        return super.onServerHeaders(serverAddress, streamId, headers);
      }
      @Override
      public Result onServerMessage(String serverAddress, String streamId, WireFormat wireFormat, Buffer msg) {
        sequence.compareAndSet(6, 7);
        return super.onServerMessage(serverAddress, streamId, wireFormat, msg);
      }
      @Override
      protected Result onServerHalfClose(String serverAddress, String streamId, int status, String statusMessage, MultiMap trailers) {
        sequence.compareAndSet(7, 8);
        return super.onServerHalfClose(serverAddress, streamId, status, statusMessage, trailers);
      }
    });

    server.callHandler(UNARY_SERVER, request -> {
      request.last().onSuccess(msg -> {
        request.response().end(Reply.newBuilder().build());
      });
    });

    Reply reply = client.request(UNARY_CLIENT)
      .compose(request -> {
        request.headers().set("foo", "bar");
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    should.assertEquals(8, sequence.get());
  }

  @Test
  public void testSink(TestContext should) throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      public Result onClientConnect(String clientAddress, String streamId) {
        sequence.compareAndSet(0, 1);
        return super.onClientConnect(clientAddress, streamId);
      }
      @Override
      public Result onClientHeaders(String clientAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(1, 2);
        return super.onClientHeaders(clientAddress, streamId, headers);
      }
      @Override
      public Result onServerConnect(String serverAddress, String streamId) {
        sequence.compareAndSet(2, 3);
        return super.onServerConnect(serverAddress, streamId);
      }
      @Override
      public Result onClientMessage(String clientAddress, WireFormat wireFormat, String streamId, Buffer msg) {
        switch (sequence.getAndIncrement()) {
          case 3:
          case 4:
            break;
          default:
            sequence.set(-1);
            break;
        }
        return super.onClientMessage(clientAddress, wireFormat, streamId, msg);
      }
      @Override
      public Result onClientHalfClose(String clientAddress, String streamId) {
        sequence.compareAndSet(5, 6);
        return super.onClientHalfClose(clientAddress, streamId);
      }
      @Override
      public Result onServerHeaders(String serverAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(6, 7);
        return super.onServerHeaders(serverAddress, streamId, headers);
      }
      @Override
      public Result onServerMessage(String serverAddress, String streamId, WireFormat wireFormat, Buffer msg) {
        sequence.compareAndSet(7, 8);
        return super.onServerMessage(serverAddress, streamId, wireFormat, msg);
      }
      @Override
      protected Result onServerHalfClose(String serverAddress, String streamId, int status, String statusMessage, MultiMap trailers) {
        sequence.compareAndSet(8, 9);
        return super.onServerHalfClose(serverAddress, streamId, status, statusMessage, trailers);
      }
    });

    server.callHandler(SINK_SERVER, request -> {
      request.last().onSuccess(msg -> {
        request.response().end(Empty.newBuilder().build());
      });
    });

    Empty empty = client.request(SINK_CLIENT)
      .compose(request -> {
        request.write(Request.newBuilder().setName("Julien").build());
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    should.assertEquals(9, sequence.get());
  }

  @Test
  public void testSource(TestContext should) throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      public Result onClientConnect(String clientAddress, String streamId) {
        sequence.compareAndSet(0, 1);
        return super.onClientConnect(clientAddress, streamId);
      }
      @Override
      public Result onClientHeaders(String clientAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(1, 2);
        return super.onClientHeaders(clientAddress, streamId, headers);
      }
      @Override
      public Result onClientMessage(String clientAddress, WireFormat wireFormat, String streamId, Buffer msg) {
        sequence.compareAndSet(2, 3);
        return super.onClientMessage(clientAddress, wireFormat, streamId, msg);
      }
      @Override
      public Result onClientHalfClose(String clientAddress, String streamId) {
        sequence.compareAndSet(3, 4);
        return super.onClientHalfClose(clientAddress, streamId);
      }
      @Override
      public Result onServerConnect(String serverAddress, String streamId) {
        sequence.compareAndSet(4, 5);
        return super.onServerConnect(serverAddress, streamId);
      }
      @Override
      public Result onServerHeaders(String serverAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(5, 6);
        return super.onServerHeaders(serverAddress, streamId, headers);
      }
      @Override
      public Result onServerMessage(String serverAddress, String streamId, WireFormat wireFormat, Buffer msg) {
        switch (sequence.getAndIncrement()) {
          case 6:
          case 7:
            break;
          default:
            sequence.set(-1);
        }
        return super.onServerMessage(serverAddress, streamId, wireFormat, msg);
      }
      @Override
      protected Result onServerHalfClose(String serverAddress, String streamId, int status, String statusMessage, MultiMap trailers) {
        sequence.compareAndSet(8, 9);
        return super.onServerHalfClose(serverAddress, streamId, status, statusMessage, trailers);
      }
    });

    server.callHandler(SOURCE_SERVER, request -> {
      request.last().onSuccess(msg -> {
        request.response().write(Reply.newBuilder().build());
        request.response().end(Reply.newBuilder().build());
      });
    });

    Reply last = client.request(SOURCE_CLIENT)
      .compose(request -> {
        request.end(Empty.getDefaultInstance());
        return request
          .response()
          .compose(GrpcReadStream::last);
      })
      .await(10, TimeUnit.SECONDS);

    should.assertEquals(9, sequence.get());
  }

  @Test
  public void testBidi(TestContext should) throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      public Result onClientConnect(String clientAddress, String streamId) {
        sequence.compareAndSet(0, 1);
        return super.onClientConnect(clientAddress, streamId);
      }
      @Override
      public Result onClientHeaders(String clientAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(1, 2);
        return super.onClientHeaders(clientAddress, streamId, headers);
      }
      @Override
      public Result onServerConnect(String serverAddress, String streamId) {
        sequence.compareAndSet(2, 3);
        return super.onServerConnect(serverAddress, streamId);
      }
      @Override
      public Result onClientMessage(String clientAddress, WireFormat wireFormat, String streamId, Buffer msg) {
        switch (sequence.getAndIncrement()) {
          case 3:
          case 4:
            break;
          default:
            sequence.set(-1);
        }
        return super.onClientMessage(clientAddress, wireFormat, streamId, msg);
      }
      @Override
      public Result onClientHalfClose(String clientAddress, String streamId) {
        sequence.compareAndSet(5, 6);
        return super.onClientHalfClose(clientAddress, streamId);
      }
      @Override
      public Result onServerHeaders(String serverAddress, String streamId, MultiMap headers) {
        sequence.compareAndSet(6, 7);
        return super.onServerHeaders(serverAddress, streamId, headers);
      }
      @Override
      public Result onServerMessage(String serverAddress, String streamId, WireFormat wireFormat, Buffer msg) {
        switch (sequence.getAndIncrement()) {
          case 7:
          case 8:
            break;
          default:
            sequence.set(-1);
        }
        return super.onServerMessage(serverAddress, streamId, wireFormat, msg);
      }
      @Override
      protected Result onServerHalfClose(String serverAddress, String streamId, int status, String statusMessage, MultiMap trailers) {
        sequence.compareAndSet(9, 10);
        return super.onServerHalfClose(serverAddress, streamId, status, statusMessage, trailers);
      }
    });

    server.callHandler(PIPE_SERVER, request -> {
      request.last().onSuccess(msg -> {
        request.response().write(Reply.newBuilder().build());
        request.response().end(Reply.newBuilder().build());
      });
    });

    Reply last = client.request(PIPE_CLIENT)
      .compose(request -> {
        request.write(Request.newBuilder().build());
        request.end(Request.newBuilder().build());
        return request
          .response()
          .compose(GrpcReadStream::last);
      })
      .await(10, TimeUnit.SECONDS);

    should.assertEquals(10, sequence.get());
  }

  @Test
  public void testUnaryFailure(TestContext should) throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      protected Result onServerHalfClose(String serverAddress, String streamId, int status, String statusMessage, MultiMap trailers) {
        if (status == GrpcStatus.ALREADY_EXISTS.code) {
          sequence.incrementAndGet();
        }
        return super.onServerHalfClose(serverAddress, streamId, status, statusMessage, trailers);
      }
    });

    server.callHandler(UNARY_SERVER, request -> {
      request.last().onSuccess(msg -> {
        request.response().status(GrpcStatus.ALREADY_EXISTS).end();
      });
    });

    try {
      Reply reply = client.request(UNARY_CLIENT)
        .compose(request -> {
          request.headers().set("foo", "bar");
          request.end(Request.newBuilder().setName("Julien").build());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(10, TimeUnit.SECONDS);
      should.fail();
    } catch (InvalidStatusException expected) {
    }
    should.assertEquals(1, sequence.get());
  }

  @Test
  public void testStreamFailure(TestContext should) throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      protected Result onServerHalfClose(String serverAddress, String streamId, int status, String statusMessage, MultiMap trailers) {
        if (status == GrpcStatus.INVALID_ARGUMENT.code) {
          sequence.incrementAndGet();
        }
        return super.onServerHalfClose(serverAddress, streamId, status, statusMessage, trailers);
      }
    });

    server.callHandler(SOURCE_SERVER, request -> {
      request.last().onSuccess(msg -> {
        request.response().write(Reply.getDefaultInstance());
        request.response().status(GrpcStatus.INVALID_ARGUMENT).end();
      });
    });

    try {
      Reply reply = client.request(SOURCE_CLIENT)
        .compose(request -> {
          request.end(Empty.getDefaultInstance());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(10, TimeUnit.SECONDS);
      should.fail();
    } catch (InvalidStatusException expected) {
    }
    should.assertEquals(1, sequence.get());
  }

  @Test
  public void testClientCancel(TestContext should) throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    vertx.eventBus().addOutboundInterceptor(new TransportInterceptor() {
      @Override
      protected Result onClientCancel(String serverAddress, String streamId) {
        sequence.incrementAndGet();
        return super.onClientCancel(serverAddress, streamId);
      }
    });
    Async async = should.async();
    server.callHandler(PIPE_SERVER, request -> {
      request.errorHandler(error -> {
        async.complete();
      });
      request.response().write(Reply.getDefaultInstance());
    });
    GrpcClientRequest<Request, Reply> request = client.request(PIPE_CLIENT).await();
    request.response().onComplete(should.asyncAssertSuccess(response -> {
      response.handler(reply -> {
        request.cancel();
      });
    }));
    request.write(Request.getDefaultInstance()).await();
    async.awaitSuccess(20_000);
    should.assertEquals(1, sequence.get());
  }
}
