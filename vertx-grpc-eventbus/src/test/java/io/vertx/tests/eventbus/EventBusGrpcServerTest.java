package io.vertx.tests.eventbus;

import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.impl.EventBusHeaders;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.StatusException;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestConstants;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class EventBusGrpcServerTest extends GrpcTestBase {

  private static final ServiceMethod<Request, Reply> UNARY = ServiceMethod.server(
    TestConstants.TEST_SERVICE,
    "Unary",
    TestConstants.REPLY_ENC,
    TestConstants.REQUEST_DEC
  );

  private static final String ADDRESS = TestConstants.TEST_SERVICE.fullyQualifiedName();

  private EventBusGrpcServer server;

  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    server = EventBusGrpcServer.server(vertx).await();
  }

  @Test
  public void testRequestReplyProtobuf() throws Exception {
    server.callHandler(UNARY, request -> request.handler(msg -> {
      Reply reply = Reply.newBuilder().setMessage("Hello " + msg.getName()).build();
      request.response().end(reply);
    }));

    Buffer payload = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name());

    Buffer body = vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).map(Message::body).await(10, TimeUnit.SECONDS);

    Reply reply = Reply.parseFrom(body.getBytes());
    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testRequestReplyJson() throws Exception {
    server.callHandler(UNARY, request -> request.handler(msg -> {
      Reply reply = Reply.newBuilder().setMessage("Hello " + msg.getName()).build();
      request.response().end(reply);
    }));

    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.JSON.name());

    JsonObject payload = new JsonObject().put("name", "Julien");
    JsonObject body = vertx.eventBus().<JsonObject> request(ADDRESS, payload, opts).map(Message::body).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", body.getString("message"));
  }

  @Test
  public void testHandlerFailure() throws TimeoutException {
    server.callHandler(UNARY, request -> request.handler(msg -> request.response().fail(new StatusException(GrpcStatus.PERMISSION_DENIED, "Not allowed"))));

    Buffer payload = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name());

    try {
      vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).await(10, TimeUnit.SECONDS);
      fail("Should have thrown");
    } catch (ReplyException e) {
      assertEquals(ReplyFailure.RECIPIENT_FAILURE, e.failureType());
      assertEquals(GrpcStatus.PERMISSION_DENIED.code, e.failureCode());
    }
  }

  @Test
  public void testHandlerThrows() throws TimeoutException {
    server.callHandler(UNARY, request -> request.handler(msg -> {
      throw new RuntimeException("Unexpected error");
    }));

    Buffer payload = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name());

    try {
      vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).await(10, TimeUnit.SECONDS);
      fail("Should have thrown");
    } catch (ReplyException e) {
      assertEquals(ReplyFailure.RECIPIENT_FAILURE, e.failureType());
      assertEquals(GrpcStatus.UNKNOWN.code, e.failureCode());
    }
  }

  @Test
  public void testUnimplementedMethod() throws TimeoutException {
    ServiceMethod<Request, Reply> otherMethod = ServiceMethod.server(TestConstants.TEST_SERVICE, "Other", TestConstants.REPLY_ENC, TestConstants.REQUEST_DEC);
    server.callHandler(otherMethod, request -> request.handler(msg -> request.response().end(Reply.getDefaultInstance())));

    Buffer payload = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name());

    try {
      vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).await(10, TimeUnit.SECONDS);
      fail("Should have thrown");
    } catch (ReplyException e) {
      assertEquals(ReplyFailure.RECIPIENT_FAILURE, e.failureType());
      assertEquals(GrpcStatus.UNIMPLEMENTED.code, e.failureCode());
    }
  }

  @Test
  public void testServerClose() throws Exception {
    server.callHandler(UNARY, request -> request.handler(msg -> {
      Reply reply = Reply.newBuilder().setMessage("Hello " + msg.getName()).build();
      request.response().end(reply);
    }));

    Buffer payload = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name());

    Buffer body = vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).map(Message::body).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", Reply.parseFrom(body.getBytes()).getMessage());

    Promise<Void> closePromise = Promise.promise();
    server.close(closePromise);
    closePromise.future().await(10, TimeUnit.SECONDS);

    try {
      vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).await(10, TimeUnit.SECONDS);
      fail("Should have thrown");
    } catch (ReplyException e) {
      assertEquals(ReplyFailure.NO_HANDLERS, e.failureType());
    }
  }

  @Test
  public void testHeaders() throws Exception {
    server.callHandler(UNARY, request -> request.handler(msg -> {
      String customHeader = request.headers().get("x-custom");
      Reply reply = Reply.newBuilder().setMessage("Header: " + customHeader).build();
      GrpcServerResponse<Request, Reply> response = request.response();
      response.headers().set("x-custom", "response_header_value");
      response.trailers().set("x-custom", "response_trailer_value");
      response.end(reply);
    }));

    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name())
      .addHeader(EventBusHeaders.HEADER_PREFIX + "x-custom", "request_header_value");

    Buffer payload = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    Message<Buffer> replyMsg = vertx.eventBus().<Buffer>request(ADDRESS, payload, opts).await();
    Buffer body = replyMsg.body();

    Reply reply = Reply.parseFrom(body.getBytes());
    assertEquals("Header: request_header_value", reply.getMessage());
    assertEquals("response_header_value", replyMsg.headers().get(EventBusHeaders.HEADER_PREFIX + "x-custom"));
    assertEquals("response_trailer_value", replyMsg.headers().get(EventBusHeaders.TRAILER_PREFIX + "x-custom"));
  }
}
