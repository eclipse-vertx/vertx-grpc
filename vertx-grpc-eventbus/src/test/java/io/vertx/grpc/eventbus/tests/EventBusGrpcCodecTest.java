package io.vertx.grpc.eventbus.tests;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.eventbus.impl.EventBusGrpcProtobufMessageCodec;
import io.vertx.grpc.eventbus.transport.v1alpha.Headers;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;
import org.junit.Test;

public class EventBusGrpcCodecTest extends EventBusGrpcTestBase {

  @Test
  public void test(TestContext should) {

    EventBus eventBus = vertx.eventBus();

    eventBus.registerCodec(EventBusGrpcProtobufMessageCodec.INSTANCE);

    TransportFrame frame = TransportFrame
      .newBuilder()
      .setStreamId(1)
      .setStreamSequence(0)
      .setHeaders(Headers.newBuilder())
      .build();

    Async latch = should.async();

    eventBus.consumer("the-address", msg -> {
      latch.complete();
    });

    eventBus.send("the-address", frame, new DeliveryOptions().setCodecName("grpc-event-bus-protobuf-codec"));
  }
}
