package io.vertx.grpc.server.tests;

import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.DefaultGrpcMessage;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;
import org.junit.Test;

public class JsonWireFormatTest extends ServerTestBase {

  private HttpClient client;

  @Override
  public void tearDown(TestContext should) {
    super.tearDown(should);
    if (client != null) {
      client.close().await();
      client = null;
    }
  }

  @Test
  public void testJsonConfig(TestContext should) {

    GrpcServerOptions options = new GrpcServerOptions()
      .setJsonReaderConfig(JsonReaderConfig.DEFAULT.ignoringUnknownFields(true))
      .setJsonWriterConfig(JsonWriterConfig.DEFAULT.omittingInsignificantWhitespace(true));

    startServer(GrpcServer.server(vertx, options).callHandler(UNARY, call -> {
      call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .end(helloReply);
      });
    }));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));

    Async async = should.async();

    client.request(HttpMethod.POST, port, "localhost", "/io.vertx.grpc.common.tests.tests.TestService/Unary")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc+json");
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          should.assertNull(resp.getHeader(GrpcHeaderNames.GRPC_STATUS));
          resp.body().onComplete(should.asyncAssertSuccess(body -> {
            String jsonString = body.getBuffer(5, body.length()).toString();
            should.assertEquals("{\"message\":\"Hello Julien\"}", jsonString);
            async.complete();
          }));
        }));
        GrpcMessage msg = GrpcMessageEncoder.JSON_OBJECT.encode(new JsonObject()
          .put("name", "Julien")
          .put("extra", 4), WireFormat.JSON);
        req.end(DefaultGrpcMessage.encode(msg));
      }));
  }
}
