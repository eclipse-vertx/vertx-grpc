package io.vertx.tests.eventbus;

import io.vertx.core.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import org.junit.Test;

public class EventBusGrpcContestTest extends EventBusGrpcTestBase {

  Context serverContext;
  Context clientContext;
  EventBusGrpcServer server;
  EventBusGrpcClient client;

  @Test
  public void testEventLoopUnary(TestContext should) {
    test(should, ThreadingModel.EVENT_LOOP, UNARY_SERVER, UNARY_CLIENT);
  }

  @Test
  public void testEventLoopBidi(TestContext should) {
    test(should, ThreadingModel.EVENT_LOOP, PIPE_SERVER, PIPE_CLIENT);
  }

  @Test
  public void testWorkerUnary(TestContext should) {
    test(should, ThreadingModel.WORKER, UNARY_SERVER, UNARY_CLIENT);
  }

  @Test
  public void testWorkerBidi(TestContext should) {
    test(should, ThreadingModel.WORKER, PIPE_SERVER, PIPE_CLIENT);
  }

  public void test(TestContext should,
                   ThreadingModel threadingModel,
                   ServiceMethod<Request, Reply> serverMethod,
                   ServiceMethod<Reply, Request> clientMethod) {

    clientContext = ((VertxInternal)vertx).createContext(threadingModel);
    serverContext = ((VertxInternal)vertx).createContext(threadingModel);
    Async async1 = should.async(2);
    clientContext.runOnContext(v -> {
      EventBusGrpcClient.client(vertx).onComplete(should.asyncAssertSuccess(c -> {
        client = c;
        async1.countDown();
      }));
    });
    serverContext.runOnContext(v -> {
      EventBusGrpcServer.server(vertx).onComplete(should.asyncAssertSuccess(s -> {
        server = s;
        async1.countDown();
      }));
    });

    async1.awaitSuccess(20_000);

    Async async2 = should.async(2);

    server.callHandler(serverMethod, new Handler<>() {
      private Request message;
      @Override
      public void handle(GrpcServerRequest<Request, Reply> request) {
        should.assertEquals(serverContext, Vertx.currentContext());
        request.handler(msg -> {
          should.assertEquals(serverContext, Vertx.currentContext());
          message = msg;
        });
        request.endHandler(v -> {
          should.assertEquals(serverContext, Vertx.currentContext());
          request.response().end(Reply.newBuilder().setMessage("Hello " + message.getName()).build());
        });
      }
    });

    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Future<GrpcClientRequest<Request, Reply>> fut = client.request(clientMethod);
      fut.onComplete(should.asyncAssertSuccess(request -> {
        should.assertEquals(ctx, Vertx.currentContext());
        request.response().onComplete(should.asyncAssertSuccess(response -> {
          should.assertEquals(ctx, Vertx.currentContext());
          response.handler(msg -> {
            should.assertEquals(ctx, Vertx.currentContext());
            async2.countDown();
          });
          response.endHandler(v2 -> {
            should.assertEquals(ctx, Vertx.currentContext());
            async2.countDown();
          });
        }));
        request.end(Request.newBuilder().setName("Julien").build());
      }));
    });

    async2.awaitSuccess();
  }
}
