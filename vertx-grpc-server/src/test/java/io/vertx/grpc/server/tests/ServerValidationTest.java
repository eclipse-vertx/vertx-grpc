package io.vertx.grpc.server.tests;

import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcMessageValidator;
import io.vertx.grpc.common.GrpcValidationException;
import io.vertx.grpc.common.tests.Empty;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestServiceGrpc;
import io.vertx.grpc.server.GrpcServer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerValidationTest extends ServerTestBase {

  private static final String EXPECTED_STATUS_MESSAGE = "Invalid message: name: name must not be empty";

  private static final GrpcMessageValidator<Request> NAME_REQUIRED = request -> {
    if (request.getName().isEmpty()) {
      throw new GrpcValidationException(Collections.singletonList(new GrpcValidationException.Violation("name", "required", "name must not be empty")));
    }
  };

  private TestServiceGrpc.TestServiceBlockingStub blockingStub() {
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    return TestServiceGrpc.newBlockingStub(channel);
  }

  private TestServiceGrpc.TestServiceStub stub() {
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    return TestServiceGrpc.newStub(channel);
  }

  @Test
  public void testValidMessageIsDelivered(TestContext should) {
    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.handler(request -> call.response().end(Reply.newBuilder().setMessage("Hello " + request.getName()).build()));
    }, NAME_REQUIRED));

    Reply reply = blockingStub().unary(Request.newBuilder().setName("Julien").build());
    should.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testInvalidMessageFailsTheCall(TestContext should) {
    AtomicInteger delivered = new AtomicInteger();
    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.handler(request -> {
        delivered.incrementAndGet();
        call.response().end(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
      });
    }, NAME_REQUIRED));

    try {
      blockingStub().unary(Request.newBuilder().build());
      should.fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
      should.assertEquals(EXPECTED_STATUS_MESSAGE, e.getStatus().getDescription());
    }
    should.assertEquals(0, delivered.get());
  }

  @Test
  public void testInvalidMessageOnLast(TestContext should) {
    AtomicInteger delivered = new AtomicInteger();
    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.last().onComplete(ar -> {
        if (ar.succeeded()) {
          delivered.incrementAndGet();
          call.response().end(Reply.newBuilder().setMessage("Hello " + ar.result().getName()).build());
        } else {
          call.response().fail(ar.cause());
        }
      });
    }, NAME_REQUIRED));

    try {
      blockingStub().unary(Request.newBuilder().build());
      should.fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
      should.assertEquals(EXPECTED_STATUS_MESSAGE, e.getStatus().getDescription());
    }
    should.assertEquals(0, delivered.get());
  }

  @Test
  public void testValidMessageOnLast(TestContext should) {
    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      call.last().onComplete(ar -> {
        if (ar.succeeded()) {
          call.response().end(Reply.newBuilder().setMessage("Hello " + ar.result().getName()).build());
        } else {
          call.response().fail(ar.cause());
        }
      });
    }, NAME_REQUIRED));

    Reply reply = blockingStub().unary(Request.newBuilder().setName("Julien").build());
    should.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testInvalidMessageInStream(TestContext should) {
    List<String> delivered = Collections.synchronizedList(new ArrayList<>());
    startServer(GrpcServer.server(vertx).callHandler(SINK, call -> {
      call.handler(request -> delivered.add(request.getName()));
      call.endHandler(v -> call.response().end(Empty.getDefaultInstance()));
    }, NAME_REQUIRED));

    Async test = should.async();
    StreamObserver<Request> items = stub().sink(new StreamObserver<Empty>() {
      @Override
      public void onNext(Empty value) {
      }
      @Override
      public void onError(Throwable t) {
        should.assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(t).getCode());
        should.assertEquals(Collections.singletonList("first"), new ArrayList<>(delivered));
        test.complete();
      }
      @Override
      public void onCompleted() {
        should.fail();
      }
    });
    items.onNext(Request.newBuilder().setName("first").build());
    items.onNext(Request.newBuilder().build());
    items.onNext(Request.newBuilder().setName("third").build());
  }

  @Test
  public void testInvalidMessageWhenPiped(TestContext should) {
    List<String> delivered = Collections.synchronizedList(new ArrayList<>());
    startServer(GrpcServer.server(vertx).callHandler(SINK, call -> {
      call.pipeTo(collector(delivered));
      call.endHandler(v -> call.response().end(Empty.getDefaultInstance()));
    }, NAME_REQUIRED));

    Async test = should.async();
    StreamObserver<Request> items = stub().sink(new StreamObserver<Empty>() {
      @Override
      public void onNext(Empty value) {
      }
      @Override
      public void onError(Throwable t) {
        should.assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(t).getCode());
        should.assertEquals(Collections.singletonList("first"), new ArrayList<>(delivered));
        test.complete();
      }
      @Override
      public void onCompleted() {
        should.fail();
      }
    });
    items.onNext(Request.newBuilder().setName("first").build());
    items.onNext(Request.newBuilder().build());
    items.onNext(Request.newBuilder().setName("third").build());
  }

  private static WriteStream<Request> collector(List<String> delivered) {
    return new WriteStream<Request>() {
      @Override
      public WriteStream<Request> exceptionHandler(Handler<Throwable> handler) {
        return this;
      }
      @Override
      public Future<Void> write(Request data) {
        delivered.add(data.getName());
        return Future.succeededFuture();
      }
      @Override
      public Future<Void> end() {
        return Future.succeededFuture();
      }
      @Override
      public WriteStream<Request> setWriteQueueMaxSize(int maxSize) {
        return this;
      }
      @Override
      public boolean writeQueueFull() {
        return false;
      }
      @Override
      public WriteStream<Request> drainHandler(Handler<Void> handler) {
        return this;
      }
    };
  }
}
