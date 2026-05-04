package io.vertx.tests.transcoding;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.transcoding.MethodTranscodingOptions;
import io.vertx.grpc.transcoding.TranscodingServiceMethod;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.server.grpc.web.EchoRequest;
import io.vertx.tests.server.grpc.web.EchoResponse;
import org.junit.Test;

import static io.vertx.tests.transcoding.ServerTranscodingTest.ECHO_REQUEST_DECODER;
import static io.vertx.tests.transcoding.ServerTranscodingTest.ECHO_RESPONSE_ENCODER;
import static io.vertx.tests.transcoding.ServerTranscodingTest.TEST_SERVICE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerTranscodingJsonFormatTest extends GrpcTestBase {

  private static final String CONTENT_TYPE = "application/json";
  private static final MultiMap HEADERS = HttpHeaders.headers()
    .add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
    .add(HttpHeaders.ACCEPT, CONTENT_TYPE)
    .copy(false);

  private static final MethodTranscodingOptions UNARY_TRANSCODING = new MethodTranscodingOptions()
    .setHttpMethod(HttpMethod.POST)
    .setPath("/hello")
    .setBody("*");

  private static final TranscodingServiceMethod<EchoRequest, EchoResponse> UNARY_CALL = TranscodingServiceMethod.server(
    TEST_SERVICE_NAME,
    "UnaryCall",
    ECHO_RESPONSE_ENCODER,
    ECHO_REQUEST_DECODER,
    UNARY_TRANSCODING
  );

  private HttpClient httpClient;
  private HttpServer httpServer;

  @Override
  public void tearDown(TestContext should) {
    if (httpServer != null) {
      httpServer.close().onComplete(should.asyncAssertSuccess());
    }
    if (httpClient != null) {
      httpClient.close().onComplete(should.asyncAssertSuccess());
    }
    super.tearDown(should);
  }

  private void startServer(TestContext should, GrpcServerOptions options) {
    httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port).setProtocolVersion(HttpVersion.HTTP_2));
    GrpcServer grpcServer = GrpcServer.server(vertx, options);
    grpcServer.callHandler(UNARY_CALL, request -> {
      request.handler(requestMsg -> {
        GrpcServerResponse<EchoRequest, EchoResponse> response = request.response();
        response.end(EchoResponse.newBuilder().setPayload(requestMsg.getPayload()).build());
      });
    });
    httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(port)).requestHandler(grpcServer);
    httpServer.listen().onComplete(should.asyncAssertSuccess());
  }

  @Test
  public void testStrictParserRejectsUnknownField(TestContext should) {
    startServer(should, new GrpcServerOptions());

    String body = new JsonObject().put("payload", "foobar").put("unknown", 42).encode();

    httpClient.request(HttpMethod.POST, "/hello").compose(req -> {
      req.headers().addAll(HEADERS);
      req.headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()));
      return req.send(body).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      // Default parser is strict, so the call should fail.
      assertTrue("expected non-2xx, got " + response.statusCode(), response.statusCode() >= 400);
    })));
  }

  @Test
  public void testServerOptionsLenientParserAcceptsUnknownField(TestContext should) {
    GrpcServerOptions lenient = new GrpcServerOptions().addEnabledFormat(
      WireFormat.JSON.ignoringUnknownFields(true)
    );

    startServer(should, lenient);

    String body = new JsonObject().put("payload", "foobar").put("unknown", 42).encode();

    httpClient.request(HttpMethod.POST, "/hello").compose(req -> {
      req.headers().addAll(HEADERS);
      req.headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()));
      return req.send(body).compose(response -> response.body().map(response));
    }).onComplete(should.asyncAssertSuccess(response -> should.verify(v -> {
      assertEquals(200, response.statusCode());
      JsonObject decoded = new JsonObject(response.body().result().toString());
      assertEquals("foobar", decoded.getString("payload"));
    })));
  }
}
