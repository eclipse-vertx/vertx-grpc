package io.vertx.grpc.eventbus.tests.eventbus;

import io.vertx.core.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import org.junit.Test;

public class EventBusGrpcContextTest extends EventBusGrpcTestBase {

  Context serverContext;
  Context clientContext;
  EventBusGrpcServer server;
  EventBusGrpcClient client;

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

    Async async = should.async(2);

    int clientMessages = clientMethod.clientStreaming() ? 128 : 1;
    int serverMessages = clientMethod.serverStreaming() ? 128 : 1;

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
            async.countDown();
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
  }
}
