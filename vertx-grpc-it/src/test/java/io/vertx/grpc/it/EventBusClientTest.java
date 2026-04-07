package io.vertx.grpc.it;

import io.grpc.examples.helloworld.GreeterGrpcClient;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.TestServiceGrpcClient;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(VertxUnitRunner.class)
public class EventBusClientTest {

  private Vertx vertx;
  private EventBusGrpcClient client;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    client = EventBusGrpcClient.create(vertx);
  }

  @After
  public void tearDown(TestContext should) {
    vertx.close().onComplete(should.asyncAssertSuccess());
  }

  @Test
  public void testGeneratedClientOverEventBus() throws Exception {
    vertx.eventBus().<Buffer> consumer(GreeterGrpcClient.SayHello.serviceName().toString(), msg -> {
      try {
        HelloRequest request = HelloRequest.parseFrom(msg.body().getBytes());
        HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
        msg.reply(Buffer.buffer(reply.toByteArray()));
      } catch (Exception e) {
        msg.fail(GrpcStatus.INTERNAL.code, e.getMessage());
      }
    });

    GreeterGrpcClient greeter = GreeterGrpcClient.create(client, SocketAddress.inetSocketAddress(0, "localhost"));

    HelloReply reply = greeter
      .sayHello(HelloRequest.newBuilder().setName("Julien").build())
      .await(10, TimeUnit.SECONDS);

    Assert.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testClientStreamingFailsOnSecondMessage() throws TimeoutException {
    vertx.eventBus().<Buffer> consumer(TestServiceGrpcClient.StreamingInputCall.serviceName().toString(), msg ->
      msg.reply(Buffer.buffer(Messages.StreamingInputCallResponse.getDefaultInstance().toByteArray()))
    );

    TestServiceGrpcClient testService = TestServiceGrpcClient.create(client, SocketAddress.inetSocketAddress(0, "localhost"));
    Promise<WriteStream<Messages.StreamingInputCallRequest>> promise = Promise.promise();

    // Don't await the response — it needs the stream to end first
    testService.streamingInputCall(promise);

    WriteStream<Messages.StreamingInputCallRequest> stream = promise.future().await(10, TimeUnit.SECONDS);

    // First write succeeds
    stream.write(Messages.StreamingInputCallRequest.getDefaultInstance()).await(10, TimeUnit.SECONDS);

    try {
      // Second write fails with UnsupportedOperationException
      stream.write(Messages.StreamingInputCallRequest.getDefaultInstance()).await(10, TimeUnit.SECONDS);
      Assert.fail("Should have thrown");
    } catch (UnsupportedOperationException e) {
      Assert.assertTrue(e.getMessage().contains("Streaming is not supported"));
    }
  }
}
