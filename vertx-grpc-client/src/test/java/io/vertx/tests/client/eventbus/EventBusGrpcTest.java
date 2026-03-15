package io.vertx.tests.client.eventbus;

import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.WireFormat;
import io.vertx.tests.client.ClientTestBase;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class EventBusGrpcTest {

  @Test
  public void testRequestReply(TestContext should) {

    Vertx vertx = Vertx.vertx();

    ServiceMethod<Reply, Request> serviceMethod = ClientTestBase.UNARY;

    vertx.eventBus().<JsonObject>consumer(serviceMethod.serviceName().toString(), msg -> {
      String action = msg.headers().get("action");
      if (serviceMethod.methodName().equals(action)) {
        vertx.setTimer(10, id -> {
          msg.reply(new JsonObject().put("message", "Hello " + msg.body().getString("name")));
        });
      } else {
        msg.fail(0, "Unknown");
      }
    });

    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();

    EventBusGrpcClientInvoker invoker = new EventBusGrpcClientInvoker(context);

    GrpcClientRequestImpl<Request, Reply> request = new GrpcClientRequestImpl<>(
      context,
      invoker,
      false,
      serviceMethod.encoder(),
      serviceMethod.decoder());

    request.format(WireFormat.JSON);
    request.serviceName(serviceMethod.serviceName());
    request.methodName(serviceMethod.methodName());

    Async async = should.async();

    request.response().onComplete(should.asyncAssertSuccess(response -> {
      response.last().onComplete(should.asyncAssertSuccess(reply -> {
        should.assertEquals("Hello Julien", reply.getMessage());
        async.complete();
      }));
    }));

    request.end(Request.newBuilder().setName("Julien").build()).await();
  }

}
