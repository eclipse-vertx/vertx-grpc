package io.vertx.grpc.server.tests;

import io.grpc.*;
import io.vertx.core.http.*;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestServiceGrpc;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

  @Test
  public void testOverrideResponseFormat(TestContext should) {

    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> {
      should.assertEquals(WireFormat.PROTOBUF, call.format());
      call
        .response()
        .format(WireFormat.JSON);
      call.handler(helloRequest -> {
        Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        GrpcServerResponse<Request, Reply> response = call.response();
        response
          .end(helloReply);
      });
    }));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Request request = Request.newBuilder().setName("Julien").build();
    try {
      Reply reply = stub.unary(request);
      fail();
    } catch (StatusRuntimeException expected) {
      assertEquals(Status.Code.CANCELLED, expected.getStatus().getCode());
    }
  }
}
