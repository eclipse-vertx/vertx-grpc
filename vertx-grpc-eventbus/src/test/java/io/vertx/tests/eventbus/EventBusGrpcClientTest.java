package io.vertx.tests.eventbus;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EventBusGrpcClientTest extends GrpcTestBase {

  private static final ServiceMethod<Reply, Request> UNARY = ServiceMethod.client(
    TestConstants.TEST_SERVICE,
    "Unary",
    TestConstants.REQUEST_ENC,
    TestConstants.REPLY_DEC
  );

  private EventBusGrpcClient client;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    client = EventBusGrpcClient.create(vertx);
  }

  @Test
  public void testRequestReplyProtobuf(TestContext testContext) throws Exception {
    vertx.eventBus().<Buffer> consumer(UNARY.serviceName().fullyQualifiedName(), msg -> {
      testContext.assertEquals("Unary", msg.headers().get("action"));
      testContext.assertEquals("PROTOBUF", msg.headers().get("grpc-wire-format"));
      try {
        Request request = Request.parseFrom(msg.body().getBytes());
        Reply reply = Reply.newBuilder().setMessage("Hello " + request.getName()).build();
        msg.reply(Buffer.buffer(reply.toByteArray()));
      } catch (Exception e) {
        msg.fail(GrpcStatus.INTERNAL.code, e.getMessage());
      }
    });

    Reply reply = client.request(UNARY)
      .compose(request -> {
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    Assert.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testRequestReplyJson(TestContext testContext) throws TimeoutException {
    vertx.eventBus().<JsonObject> consumer(UNARY.serviceName().fullyQualifiedName(), msg -> {
      testContext.assertEquals("Unary", msg.headers().get("action"));
      testContext.assertEquals("JSON", msg.headers().get("grpc-wire-format"));
      String name = msg.body().getString("name");
      msg.reply(new JsonObject().put("message", "Hello " + name));
    });

    Reply reply = client.request(UNARY)
      .compose(request -> {
        request.format(WireFormat.JSON);
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    Assert.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testRequestReplyWithDelay() throws Exception {
    vertx.eventBus().<Buffer> consumer(UNARY.serviceName().fullyQualifiedName(), msg -> vertx.setTimer(50, id -> {
      try {
        Request request = Request.parseFrom(msg.body().getBytes());
        Reply reply = Reply.newBuilder().setMessage("Delayed " + request.getName()).build();
        msg.reply(Buffer.buffer(reply.toByteArray()));
      } catch (Exception e) {
        msg.fail(GrpcStatus.INTERNAL.code, e.getMessage());
      }
    }));

    Reply reply = client.request(UNARY)
      .compose(request -> {
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    Assert.assertEquals("Delayed Julien", reply.getMessage());
  }

  @Test
  public void testNoHandler() throws TimeoutException {
    try {
      client.request(UNARY)
        .compose(request -> {
          request.end(Request.newBuilder().setName("Julien").build());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(10, TimeUnit.SECONDS);
      Assert.fail("Should have thrown");
    } catch (InvalidStatusException e) {
      Assert.assertEquals(GrpcStatus.UNAVAILABLE, e.actualStatus());
    }
  }

  @Test
  public void testConsumerFailureUnmappedCode() throws TimeoutException {
    vertx.eventBus().<Buffer> consumer(UNARY.serviceName().fullyQualifiedName(), msg -> msg.fail(500, "Service error"));

    try {
      client.request(UNARY)
        .compose(request -> {
          request.end(Request.newBuilder().setName("Julien").build());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(10, TimeUnit.SECONDS);
      Assert.fail("Should have thrown");
    } catch (InvalidStatusException e) {
      Assert.assertEquals(GrpcStatus.INTERNAL, e.actualStatus());
    }
  }

  @Test
  public void testConsumerFailureGrpcStatusCodeMapping() throws TimeoutException {
    for (GrpcStatus status : GrpcStatus.values()) {
      if (status == GrpcStatus.OK) {
        continue;
      }

      MessageConsumer<Buffer> consumer = vertx.eventBus().consumer(UNARY.serviceName().fullyQualifiedName(), msg -> msg.fail(status.code, status.name()));

      consumer.completion().await(10, TimeUnit.SECONDS);

      try {
        client.request(UNARY)
          .compose(request -> {
            request.end(Request.newBuilder().setName("Julien").build());
            return request.response();
          })
          .compose(GrpcReadStream::last)
          .await(10, TimeUnit.SECONDS);
        Assert.fail("Should have thrown for " + status);
      } catch (InvalidStatusException e) {
        Assert.assertEquals(status, e.actualStatus());
      }

      consumer.unregister().await(10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testRequestHeaders() throws Exception {
    vertx.eventBus().<Buffer> consumer(UNARY.serviceName().fullyQualifiedName(), msg -> {
      String customHeader = msg.headers().get("x-custom");
      Reply reply = Reply.newBuilder().setMessage("Header: " + customHeader).build();
      msg.reply(Buffer.buffer(reply.toByteArray()));
    });

    Reply reply = client.request(UNARY)
      .compose(request -> {
        request.headers().add("x-custom", "test-value");
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    Assert.assertEquals("Header: test-value", reply.getMessage());
  }

  @Test
  public void testDeadlineExceeded() throws TimeoutException {
    vertx.eventBus().<Buffer> consumer(UNARY.serviceName().fullyQualifiedName(), msg -> {
      // do nothing
    });

    try {
      client.request(UNARY)
        .compose(request -> {
          request.timeout(1, TimeUnit.SECONDS);
          request.end(Request.newBuilder().setName("Julien").build());
          return request.response();
        })
        .compose(GrpcReadStream::last)
        .await(10, TimeUnit.SECONDS);
      Assert.fail("Should have thrown");
    } catch (InvalidStatusException e) {
      Assert.assertEquals(GrpcStatus.DEADLINE_EXCEEDED, e.actualStatus());
    }
  }
}
