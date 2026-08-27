package io.vertx.grpc.eventbus.tests;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.eventbus.impl.EventBusGrpcJsonMessageCodec;
import io.vertx.grpc.eventbus.impl.EventBusGrpcProtobufMessageCodec;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.test.fakecluster.FakeClusterManager;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.grpc.eventbus.tests.EventBusGrpcTestBase.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
public class EventBusGrpcClusteringTest {

  Vertx vertx1;
  Vertx vertx2;
  EventBusGrpcServer server;
  EventBusGrpcClient client;
  MessageCodecSpy<TransportFrame, TransportFrame> clientProtoCodecSpy;
  MessageCodecSpy<TransportFrame, TransportFrame> serverProtoCodecSpy;
  MessageCodecSpy<TransportFrame, TransportFrame> clientJsonCodecSpy;
  MessageCodecSpy<TransportFrame, TransportFrame> serverJsonCodecSpy;

  @Before
  public void setup() {
    vertx1 = Vertx.builder().withClusterManager(new FakeClusterManager()).buildClustered().await();
    vertx2 = Vertx.builder().withClusterManager(new FakeClusterManager()).buildClustered().await();
    clientProtoCodecSpy = new MessageCodecSpy<>(EventBusGrpcProtobufMessageCodec.INSTANCE);
    serverProtoCodecSpy = new MessageCodecSpy<>(EventBusGrpcProtobufMessageCodec.INSTANCE);
    clientJsonCodecSpy = new MessageCodecSpy<>(EventBusGrpcJsonMessageCodec.INSTANCE);
    serverJsonCodecSpy = new MessageCodecSpy<>(EventBusGrpcJsonMessageCodec.INSTANCE);
    vertx1.eventBus().registerCodec(clientProtoCodecSpy);
    vertx2.eventBus().registerCodec(serverProtoCodecSpy);
    vertx1.eventBus().registerCodec(clientJsonCodecSpy);
    vertx2.eventBus().registerCodec(serverJsonCodecSpy);
  }

  @After
  public void tearDown() {
    try {
      vertx1.close().await();
      vertx2.close().await();
    } finally {
      vertx1 = null;
      vertx2 = null;
      client = null;
      server = null;
    }
  }

  @Test
  public void testRequestReplyProtobuf() throws Exception {
    testRequestReply(WireFormat.PROTOBUF, WireFormat.PROTOBUF);
  }

  @Test
  public void testRequestReplyJson() throws Exception {
    testRequestReply(WireFormat.JSON, WireFormat.JSON);
  }

  @Test
  public void testRequestReplyMixed() throws Exception {
    testRequestReply(WireFormat.JSON, WireFormat.PROTOBUF);
  }

  private void testRequestReply(WireFormat clientWireFormat, WireFormat serverWireFormat) throws Exception {

    server = EventBusGrpcServer.server(vertx1, new EventBusGrpcServerOptions().setWireFormat(serverWireFormat)).await();
    client = EventBusGrpcClient.client(vertx2, new EventBusGrpcClientOptions().setWireFormat(clientWireFormat)).await();

    server.callHandler(UNARY_SERVER, request -> request.handler(msg -> {
      Reply reply = Reply.newBuilder().setMessage("Hello " + msg.getName()).build();
      request.response().end(reply);
    }));

    Reply reply = client.request(UNARY_CLIENT)
      .compose(request -> {
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());

    assertEquals(0, clientJsonCodecSpy.decodeOps.get());
    assertEquals(0, clientJsonCodecSpy.encodeOps.get());
    assertEquals(0, serverJsonCodecSpy.decodeOps.get());
    assertEquals(0, serverJsonCodecSpy.encodeOps.get());
    assertEquals(0, clientProtoCodecSpy.decodeOps.get());
    assertEquals(0, clientJsonCodecSpy.encodeOps.get());
    assertEquals(0, serverJsonCodecSpy.decodeOps.get());
    assertEquals(0, serverJsonCodecSpy.encodeOps.get());
  }

  @Test
  public void testStreamingProtobuf(TestContext should) {
    testStreaming(should, WireFormat.PROTOBUF, WireFormat.PROTOBUF);
  }

  @Test
  public void testStreamingJson(TestContext should) {
    testStreaming(should, WireFormat.JSON, WireFormat.JSON);
  }

  @Test
  public void testStreamingMixed(TestContext should) {
    testStreaming(should, WireFormat.JSON, WireFormat.PROTOBUF);
  }

  private void testStreaming(TestContext should, WireFormat clientWireFormat, WireFormat serverWireFormat) {

    client = EventBusGrpcClient.client(vertx1, new EventBusGrpcClientOptions().setWireFormat(clientWireFormat)).await();
    server = EventBusGrpcServer.server(vertx2, new EventBusGrpcServerOptions().setWireFormat(serverWireFormat)).await();

    server.callHandler(PIPE_SERVER, request -> {
      GrpcServerResponse<Request, Reply> response = request.response();
      request.handler(msg -> {
        response.write(Reply.newBuilder().setMessage("Hello " + msg.getName()).build());
      });
      request.endHandler(v -> {
        response.end();
      });
    });

    int num = 16;

    List<String> expected = IntStream
      .range(0, num)
      .mapToObj(val -> "Hello msg-" + val)
      .collect(Collectors.toList());

    GrpcClientRequest<Request, Reply> request = client.request(PIPE_CLIENT).await();
    Async async = should.async();
    request.response().onComplete(should.asyncAssertSuccess(response -> {
      List<String> result = new ArrayList<>();
      response.handler(reply -> {
        result.add(reply.getMessage());
      });
      response.endHandler(v -> {
        should.assertEquals(expected, result);
        async.complete();
      });
    }));

    for (int i = 0;i < num;i++) {
      request.write(Request.newBuilder().setName("msg-" + i).build());
    }
    request.end();

    async.awaitSuccess(20_000);

    clientJsonCodecSpy.assertDecodedUsage(clientWireFormat == WireFormat.JSON);
    clientJsonCodecSpy.assertEncodedUsage(serverWireFormat == WireFormat.JSON);
    clientProtoCodecSpy.assertDecodedUsage(clientWireFormat == WireFormat.PROTOBUF);
    clientProtoCodecSpy.assertEncodedUsage(serverWireFormat == WireFormat.PROTOBUF);
    serverJsonCodecSpy.assertDecodedUsage(serverWireFormat == WireFormat.JSON);
    serverJsonCodecSpy.assertEncodedUsage(clientWireFormat == WireFormat.JSON);
    serverProtoCodecSpy.assertDecodedUsage(serverWireFormat == WireFormat.PROTOBUF);
    serverProtoCodecSpy.assertEncodedUsage(clientWireFormat == WireFormat.PROTOBUF);
  }

  static class MessageCodecSpy<S, R> implements MessageCodec<S, R> {

    final MessageCodec<S, R> delegate;
    final AtomicInteger encodeOps = new AtomicInteger();
    final AtomicInteger decodeOps = new AtomicInteger();

    public MessageCodecSpy(MessageCodec<S, R> delegate) {
      this.delegate = delegate;
    }

    public void assertDecodedUsage(boolean expectUsed) {
      if (expectUsed) {
        assertTrue(decodeOps.get() > 0);
      } else {
        assertEquals(0, decodeOps.get());
      }
    }

    public void assertEncodedUsage(boolean expectUsed) {
      if (expectUsed) {
        assertTrue(encodeOps.get() > 0);
      } else {
        assertEquals(0, encodeOps.get());
      }
    }

    @Override
    public void encodeToWire(Buffer buffer, S s) {
      encodeOps.incrementAndGet();
      delegate.encodeToWire(buffer, s);
    }

    @Override
    public R decodeFromWire(int pos, Buffer buffer) {
      decodeOps.incrementAndGet();
      return delegate.decodeFromWire(pos, buffer);
    }

    @Override
    public R transform(S s) {
      return delegate.transform(s);
    }

    @Override
    public String name() {
      return delegate.name();
    }

    @Override
    public byte systemCodecID() {
      return delegate.systemCodecID();
    }
  }
}
