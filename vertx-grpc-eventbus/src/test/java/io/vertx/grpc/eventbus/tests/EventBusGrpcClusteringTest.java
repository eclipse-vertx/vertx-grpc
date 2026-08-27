package io.vertx.grpc.eventbus.tests;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.grpc.eventbus.tests.EventBusGrpcTestBase.*;
import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class EventBusGrpcClusteringTest {

  Vertx vertx1;
  Vertx vertx2;
  EventBusGrpcServer server;
  EventBusGrpcClient client;

  @Before
  public void setup() {
    vertx1 = Vertx.builder().withClusterManager(new FakeClusterManager()).buildClustered().await();
    vertx2 = Vertx.builder().withClusterManager(new FakeClusterManager()).buildClustered().await();
    server = EventBusGrpcServer.server(vertx1).await();
    client = EventBusGrpcClient.client(vertx2).await();
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
    testRequestReply(WireFormat.PROTOBUF);
  }

  @Test
  public void testRequestReplyJson() throws Exception {
    testRequestReply(WireFormat.JSON);
  }

  private void testRequestReply(WireFormat wireFormat) throws Exception {
    server.callHandler(UNARY_SERVER, request -> request.handler(msg -> {
      Reply reply = Reply.newBuilder().setMessage("Hello " + msg.getName()).build();
      request.response().end(reply);
    }));

    Reply reply = client.request(UNARY_CLIENT)
      .compose(request -> {
        request.format(wireFormat);
        request.end(Request.newBuilder().setName("Julien").build());
        return request.response();
      })
      .compose(GrpcReadStream::last)
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testStreamingProtobuf(TestContext should) throws Exception {
    testStreaming(should, WireFormat.PROTOBUF);
  }

  @Test
  public void testStreamingJson(TestContext should) throws Exception {
    testStreaming(should, WireFormat.JSON);
  }

  private void testStreaming(TestContext should, WireFormat wireFormat) throws Exception {
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
    request.format(wireFormat);
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
  }
}
