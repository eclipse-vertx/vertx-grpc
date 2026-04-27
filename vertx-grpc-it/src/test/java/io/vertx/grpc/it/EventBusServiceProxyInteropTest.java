package io.vertx.grpc.it;

import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.it.proxyinterop.GreeterClient;
import io.vertx.grpc.it.proxyinterop.GreeterGrpcClient;
import io.vertx.grpc.it.proxyinterop.GreeterGrpcService;
import io.vertx.grpc.it.proxyinterop.GreeterProxy;
import io.vertx.grpc.it.proxyinterop.GreeterService;
import io.vertx.grpc.it.proxyinterop.HelloJavaReply;
import io.vertx.grpc.it.proxyinterop.HelloReply;
import io.vertx.grpc.it.proxyinterop.HelloRequest;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.serviceproxy.ServiceProxyBuilder;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class EventBusServiceProxyInteropTest extends GrpcTestBase {

  private static final String ADDRESS = "proxyinterop.Greeter";

  @Test
  public void testProxyClientCallsGrpcServer(TestContext should) throws Exception {
    EventBusGrpcServer server = EventBusGrpcServer.create(vertx);
    server.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    }));

    GreeterProxy proxy = new ServiceProxyBuilder(vertx)
      .setAddress(ADDRESS)
      .setOptions(new DeliveryOptions().addHeader("grpc-wire-format", WireFormat.JSON.name()))
      .build(GreeterProxy.class);

    HelloJavaReply reply = proxy.SayHello("Julien").await(10, TimeUnit.SECONDS);

    should.assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testGrpcClientCallsProxyServer(TestContext should) throws Exception {
    GreeterProxy impl = name -> Future.succeededFuture(new HelloJavaReply().setMessage("Hello " + name));
    MessageConsumer<JsonObject> consumer = new ServiceBinder(vertx)
      .setAddress(ADDRESS)
      .register(GreeterProxy.class, impl);

    try {
      EventBusGrpcClient client = EventBusGrpcClient.create(vertx);
      GreeterClient greeter = GreeterGrpcClient.create(client, WireFormat.JSON);

      HelloReply reply = greeter
        .sayHello(HelloRequest.newBuilder().setName("Julien").build())
        .await(10, TimeUnit.SECONDS);

      should.assertEquals("Hello Julien", reply.getMessage());
    } finally {
      consumer.unregister().await(10, TimeUnit.SECONDS);
    }
  }
}
