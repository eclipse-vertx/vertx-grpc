package io.vertx.grpc.eventbus.tests;

import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.MethodCardinality;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class EventBusGrpcContextTest extends EventBusGrpcTestBase {

  final AtomicInteger traceActivity = new AtomicInteger();
  ContextInternal serverContext;
  ContextInternal clientContext;
  EventBusGrpcServer server;
  EventBusGrpcClient client;

  @Override
  protected Vertx newVertx() {
    return Vertx.builder().withTracer(options -> new VertxTracer<>() {
      @Override
      public <R> Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
        if (policy != TracingPolicy.IGNORE) {
          traceActivity.incrementAndGet();
        }
        return VertxTracer.super.sendRequest(context, kind, policy, request, operation, headers, tagExtractor);
      }
      @Override
      public <R> Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
        if (policy != TracingPolicy.IGNORE) {
          traceActivity.incrementAndGet();
        }
        return VertxTracer.super.receiveRequest(context, kind, policy, request, operation, headers, tagExtractor);
      }
    }).build();
  }

  @Override
  public void setUp(TestContext should) {
    traceActivity.set(0);
    super.setUp(should);
  }

  private void init(TestContext should, ThreadingModel threadingModel) {
    initServer(should, threadingModel);
    initClient(should, threadingModel);
  }

  private void initClient(TestContext should, ThreadingModel threadingModel) {
    Async async = should.async();
    clientContext = ((VertxInternal)vertx).createContext(threadingModel);
    EventBusGrpcClient.client(vertx).onComplete(should.asyncAssertSuccess(c -> {
      client = c;
      async.countDown();
    }));
    async.awaitSuccess(20_000);
  }

  private void initServer(TestContext should, ThreadingModel threadingModel) {
    Async async = should.async();
    serverContext = ((VertxInternal)vertx).createContext(threadingModel);
    serverContext.runOnContext(v -> {
      EventBusGrpcServer.server(vertx).onComplete(should.asyncAssertSuccess(s -> {
        server = s;
        async.countDown();
      }));
    });

    async.awaitSuccess(20_000);
  }

  @Test
  public void testEventLoopUnary(TestContext should) {
    init(should, ThreadingModel.EVENT_LOOP);
    test(should, UNARY_SERVER, UNARY_CLIENT);
  }

  @Test
  public void testEventLoopBidi(TestContext should) {
    init(should, ThreadingModel.EVENT_LOOP);
    test(should, PIPE_SERVER, PIPE_CLIENT);
  }

  @Test
  public void testWorkerUnary(TestContext should) {
    init(should, ThreadingModel.WORKER);
    test(should, UNARY_SERVER, UNARY_CLIENT);
  }

  @Test
  public void testWorkerBidi(TestContext should) {
    init(should, ThreadingModel.WORKER);
    test(should, PIPE_SERVER, PIPE_CLIENT);
  }

  public void test(TestContext should,
                   ServiceMethod<Request, Reply> serverMethod,
                   ServiceMethod<Reply, Request> clientMethod) {

    Async async = should.async();

    int clientMessages = clientMethod.cardinality() == MethodCardinality.CLIENT_STREAMING || clientMethod.cardinality() == MethodCardinality.BIDI_STREAMING ? 128 : 1;
    int serverMessages = clientMethod.cardinality() == MethodCardinality.SERVER_STREAMING || clientMethod.cardinality() == MethodCardinality.BIDI_STREAMING ? 128 : 1;

    server.callHandler(serverMethod, new Handler<>() {
      private Request message;
      @Override
      public void handle(GrpcServerRequest<Request, Reply> request) {
        ContextInternal current = ContextInternal.current();
        should.assertTrue(current.isDuplicate());
        should.assertEquals(serverContext.executor(), current.executor());
        request.handler(msg -> {
          should.assertEquals(current, Vertx.currentContext());
          message = msg;
        });
        request.endHandler(v -> {
          should.assertEquals(current, Vertx.currentContext());
          for (int idx = 0;idx < serverMessages;idx++) {
            request.response().write(Reply.newBuilder().setMessage("reply-" + idx).build());
          }
          request.response().end();
        });
      }
    });

    clientContext.runOnContext(v -> {
      Future<GrpcClientRequest<Request, Reply>> fut = client.request(clientMethod);
      fut.onComplete(should.asyncAssertSuccess(request -> {
        should.assertEquals(clientContext, Vertx.currentContext());
        request.response().onComplete(should.asyncAssertSuccess(response -> {
          should.assertEquals(clientContext, Vertx.currentContext());
          response.handler(msg -> {
            should.assertEquals(clientContext, Vertx.currentContext());
          });
          response.endHandler(v2 -> {
            should.assertEquals(clientContext, Vertx.currentContext());
            async.countDown();
          });
        }));
        for (int idx = 0;idx < clientMessages;idx++) {
          request.write(Request.newBuilder().setName("request-" + idx).build());
        }
        request.end();
      }));
    });

    async.awaitSuccess();

    should.assertEquals(0, traceActivity.get());
  }
}
