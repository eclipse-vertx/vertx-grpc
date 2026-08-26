package io.vertx.grpc.eventbus.tests;

import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import org.junit.Before;
import org.junit.Test;

public class EventBusGrpcCardinalityInteropTest extends EventBusGrpcTestBase {

  private EventBusGrpcServer server;
  private EventBusGrpcClient client;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    server = EventBusGrpcServer.server(vertx).await();
    client = EventBusGrpcClient.client(vertx).await();
  }

  @Test
  public void testUnaryService() {
    server.callHandler(UNARY_SERVER, request -> request
      .endHandler(v -> request
        .response()
        .end(Reply.getDefaultInstance())));

    for (int i = 0;i < 4;i++) {
      boolean clientStreaming = (i & 0x01) != 0;
      boolean serverStreaming = (i & 0x02) != 0;
      ServiceMethod<Reply, Request> a = ServiceMethod.client(UNARY_CLIENT.serviceName(), UNARY_CLIENT.methodName(),
        clientStreaming,
        serverStreaming, UNARY_CLIENT.encoder(), UNARY_CLIENT.decoder());
      client.request(a)
        .compose(request -> {
          request.end(Request.getDefaultInstance());
          return request
            .response()
            .compose(GrpcReadStream::last);
        }).await();
    }
  }

  @Test
  public void testBidiService() {
    server.callHandler(PIPE_SERVER, request -> request
      .handler(msg -> request.response().write(Reply.getDefaultInstance()))
      .endHandler(v -> request.response().end()));

    for (int i = 0;i < 4;i++) {
      boolean clientStreaming = (i & 0x01) != 0;
      boolean serverStreaming = (i & 0x02) != 0;
      ServiceMethod<Reply, Request> a = ServiceMethod.client(PIPE_SERVER.serviceName(), PIPE_SERVER.methodName(),
        clientStreaming,
        serverStreaming, UNARY_CLIENT.encoder(), UNARY_CLIENT.decoder());
      client.request(a)
        .compose(request -> {
          request.end(Request.getDefaultInstance());
          return request
            .response()
            .compose(GrpcReadStream::last);
        }).await();
    }
  }
}
