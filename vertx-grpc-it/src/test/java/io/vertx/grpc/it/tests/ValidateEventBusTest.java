package io.vertx.grpc.it.tests;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.Service;
import org.junit.After;

public class ValidateEventBusTest extends ValidateTestBase {

  private EventBusGrpcClient client;

  @Override
  @After
  public void tearDown(TestContext should) {
    if (client != null) {
      client.close().await();
      client = null;
    }
    super.tearDown(should);
  }

  @Override
  protected void deploy(Service service) {
    EventBusGrpcServer.server(vertx).await().addService(service);
  }

  @Override
  protected <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method) {
    if (client == null) {
      client = EventBusGrpcClient.client(vertx).await();
    }
    return client.request(method);
  }
}
