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
import io.vertx.grpc.eventbus.EventBusHeaders;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.StatusException;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.Empty;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestConstants;
import io.vertx.tests.common.grpc.Tests;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    server = EventBusGrpcServer.create(vertx);
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
    Assert.assertEquals("Hello Julien", reply.getMessage());
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

    Buffer payload = new JsonObject().put("name", "Julien").toBuffer();
    Buffer body = vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).map(Message::body).await(10, TimeUnit.SECONDS);

    String message = new JsonObject(body).getString("message");
    Assert.assertEquals("Hello Julien", message);
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
      Assert.fail("Should have thrown");
    } catch (ReplyException e) {
      Assert.assertEquals(ReplyFailure.RECIPIENT_FAILURE, e.failureType());
      Assert.assertEquals(GrpcStatus.PERMISSION_DENIED.code, e.failureCode());
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
      Assert.fail("Should have thrown");
    } catch (ReplyException e) {
      Assert.assertEquals(ReplyFailure.RECIPIENT_FAILURE, e.failureType());
      Assert.assertEquals(GrpcStatus.UNKNOWN.code, e.failureCode());
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
      Assert.fail("Should have thrown");
    } catch (ReplyException e) {
      Assert.assertEquals(ReplyFailure.RECIPIENT_FAILURE, e.failureType());
      Assert.assertEquals(GrpcStatus.UNIMPLEMENTED.code, e.failureCode());
    }
  }

  @Test
  public void testStreamingMethodRejected() throws TimeoutException {
    ServiceMethod<Empty, Reply> sourceMethod = ServiceMethod.server(TestConstants.TEST_SERVICE, "Source", TestConstants.REPLY_ENC, TestConstants.EMPTY_DEC);
    Service service = Service.service(TestConstants.TEST_SERVICE, Tests.getDescriptor().findServiceByName("TestService"))
      .bind(sourceMethod, request -> request.handler(msg -> request.response().end(Reply.getDefaultInstance())))
      .build();
    server.addService(service);

    Buffer payload = Buffer.buffer(Empty.getDefaultInstance().toByteArray());
    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Source")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name());

    try {
      vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).await(10, TimeUnit.SECONDS);
      Assert.fail("Should have thrown");
    } catch (ReplyException e) {
      Assert.assertEquals(ReplyFailure.RECIPIENT_FAILURE, e.failureType());
      Assert.assertEquals(GrpcStatus.UNIMPLEMENTED.code, e.failureCode());
      Assert.assertTrue(e.getMessage(), e.getMessage().toLowerCase().contains("streaming"));
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

    Assert.assertEquals("Hello Julien", Reply.parseFrom(body.getBytes()).getMessage());

    Promise<Void> closePromise = Promise.promise();
    server.close(closePromise);
    closePromise.future().await(10, TimeUnit.SECONDS);

    try {
      vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).await(10, TimeUnit.SECONDS);
      Assert.fail("Should have thrown");
    } catch (ReplyException e) {
      Assert.assertEquals(ReplyFailure.NO_HANDLERS, e.failureType());
    }
  }

  @Test
  public void testRequestHeaders() throws Exception {
    server.callHandler(UNARY, request -> request.handler(msg -> {
      String customHeader = request.headers().get("x-custom");
      Reply reply = Reply.newBuilder().setMessage("Header: " + customHeader).build();
      request.response().end(reply);
    }));

    DeliveryOptions opts = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, "Unary")
      .addHeader(EventBusHeaders.WIRE_FORMAT, WireFormat.PROTOBUF.name())
      .addHeader("x-custom", "test-value");

    Buffer payload = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    Buffer body = vertx.eventBus().<Buffer> request(ADDRESS, payload, opts).map(Message::body).await(10, TimeUnit.SECONDS);

    Reply reply = Reply.parseFrom(body.getBytes());
    Assert.assertEquals("Header: test-value", reply.getMessage());
  }
}
