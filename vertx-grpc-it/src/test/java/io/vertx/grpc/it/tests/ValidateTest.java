package io.vertx.grpc.it.tests;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.client.GrpcIoClientChannel;
import org.junit.After;
import org.junit.Test;
import vertx.weather.v1.WeatherGrpc;

import java.time.LocalDateTime;

public class ValidateTest extends ValidateTestBase {

  private GrpcClient client;
  private GrpcIoClient ioClient;

  @Override
  @After
  public void tearDown(TestContext should) {
    if (client != null) {
      client.close().await();
      client = null;
    }
    if (ioClient != null) {
      ioClient.close().await();
      ioClient = null;
    }
    super.tearDown(should);
  }

  @Override
  protected void deploy(Service service) {
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.addService(service);
    vertx
      .createHttpServer()
      .requestHandler(grpcServer)
      .listen(port)
      .await();
  }

  @Override
  protected <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method) {
    if (client == null) {
      client = GrpcClient.client(vertx);
    }
    return client.request(SocketAddress.inetSocketAddress(port, "localhost"), method);
  }

  @Test
  public void testGrpcJavaClientSeesInvalidArgument(TestContext should) {
    ioClient = GrpcIoClient.client(vertx);
    try {
      WeatherGrpc
        .newBlockingStub(new GrpcIoClientChannel(ioClient, SocketAddress.inetSocketAddress(port, "localhost")))
        .getWeather(request(91, 0, LocalDateTime.now().plusDays(1)));
      should.fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
      should.assertTrue(e.getStatus().getDescription().contains("latitude"), e.getStatus().getDescription());
    }
    should.assertEquals(0, delivered.size());
  }
}
