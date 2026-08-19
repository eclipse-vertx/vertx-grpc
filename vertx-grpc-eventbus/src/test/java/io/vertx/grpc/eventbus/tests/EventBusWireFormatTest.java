package io.vertx.grpc.eventbus.tests;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.impl.EventBusHeaders;
import org.junit.Before;
import org.junit.Test;

import java.util.function.BiConsumer;

public class EventBusWireFormatTest extends EventBusGrpcTestBase {

  private EventBusGrpcServer server;
  private EventBusGrpcClient client;
  private volatile BiConsumer<String, Buffer> payloadHandler;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    server = EventBusGrpcServer.server(vertx).await();
    client = EventBusGrpcClient.client(vertx).await();
  }

  private void addPayloadInterceptor(TestContext should, BiConsumer<String, Object> payloadHandler) {
    vertx.eventBus().addOutboundInterceptor(ctx -> {
      MultiMap headers = ctx
        .message()
        .headers();
      String wireFormat = headers.get(EventBusHeaders.WIRE_FORMAT);
      if (wireFormat != null) {
        try {
          payloadHandler.accept(wireFormat, ctx.message().body());
        } catch (Throwable failure) {
          should.fail(failure);
        }
      }
      ctx.next();
    });
  }

  @Test
  public void testUnary(TestContext should) {

    addPayloadInterceptor(should, (format, body) -> {
      should.assertEquals("json", format);
      should.assertEquals(JsonObject.class, body.getClass());
    });

    server.callHandler(UNARY_SERVER, request -> request
      .endHandler(v -> request
        .response()
        .end(Reply.getDefaultInstance())));

    client.request(UNARY_CLIENT)
      .compose(request -> {
        request.format(WireFormat.JSON);
        request.end(Request.getDefaultInstance());
        return request
          .response()
          .compose(GrpcReadStream::last);
      }).await();
  }

  @Test
  public void testStreaming(TestContext should) {

    addPayloadInterceptor(should, (format, body) -> {
      should.assertEquals("json", format);
      if (body == null) {
        // OK
      } else {
        Buffer buffer = (Buffer) body;
        JsonObject json = new JsonObject(buffer);
        JsonObject message = json.getJsonObject("message");
        if (message != null) {
          Object str = message.getValue("string");
          should.assertEquals(String.class, str.getClass());
          JsonObject payload = new JsonObject((String)str);
        }
      }
    });

    server.callHandler(PIPE_SERVER, request -> request
      .handler(msg -> {
        request.response().write(Reply.newBuilder().setMessage("reply-to-" + msg.getName()).build());
      })
      .endHandler(v -> request
        .response()
        .end(Reply.getDefaultInstance())));

    int num = 8;

    client.request(PIPE_CLIENT)
      .compose(request -> {
        request.format(WireFormat.JSON);
        for (int i = 0;i < num;i++) {
          request.write(Request.newBuilder().setName("msg-" + i).build());
        }
        request.end();
        return request
          .response()
          .compose(GrpcReadStream::last);
      }).await();
  }
}
