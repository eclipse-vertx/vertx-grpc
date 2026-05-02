package io.vertx.tests.server;

import io.vertx.core.http.*;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import org.junit.Test;

public class FormatSupportTest extends ServerTestBase {

  private HttpClient client;

  @Override
  public void setUp(TestContext should) {
    super.setUp(should);

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );
  }

  @Override
  public void tearDown(TestContext should) {
    super.tearDown(should);
    if (client != null) {
      client.close();
    }
  }

  @Test
  public void testDisableJson(TestContext should) {
    testDisableFormat(should, "application/grpc+json", WireFormat.JSON);
  }

  @Test
  public void testDisableProtobuf(TestContext should) {
    testDisableFormat(should, "application/grpc", WireFormat.PROTOBUF);
  }

  @Test
  public void testDisableJsonAlsoRejectsTranscoding(TestContext should) {
    testDisableFormat(should, "application/json", WireFormat.JSON);
  }

  private void testDisableFormat(TestContext should, String contentType, WireFormat format) {
    GrpcServerOptions options = new GrpcServerOptions().removeEnabledFormat(format);
    startServer(GrpcServer.server(vertx, options));

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(request -> {
        request.putHeader(GrpcHeaderNames.GRPC_ENCODING, "identity");
        request.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
        request.send();
        return request.response().map(HttpClientResponse::statusCode);
      }).onComplete(should.asyncAssertSuccess(status -> {
        should.assertEquals(415, status);
      }));
  }
}
