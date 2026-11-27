package io.vertx.grpc.server.tests;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestServiceGrpc;
import io.vertx.grpc.server.GrpcServer;
import org.junit.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * The server side of the test cases of https://github.com/grpc/grpc/blob/master/doc/compression.md
 */
public class CompressionSpecTest extends ServerTestBase {

  private final AtomicBoolean decoded = new AtomicBoolean();

  @Test
  public void testUnsupportedRequestEncoding() {
    testUnsupportedRequestEncoding(GrpcMediaType.GRPC);
  }

  @Test
  public void testUnsupportedRequestEncodingWeb() {
    testUnsupportedRequestEncoding(GrpcMediaType.GRPC_WEB_PROTO);
  }

  private void testUnsupportedRequestEncoding(CharSequence contentType) {
    HttpClientResponse response = call(contentType, "snappy", compressed());
    assertFalse("The message must not be decoded", decoded.get());
    assertEquals("" + GrpcStatus.UNIMPLEMENTED.code, response.getHeader(GrpcHeaderNames.GRPC_STATUS));

    String message = message(response);
    assertTrue(message, message.contains("snappy"));
    assertTrue(message, message.contains("identity"));
    assertTrue(message, message.contains("gzip"));

    String accepted = response.getHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING);
    assertFalse(accepted, accepted.contains("snappy"));
  }

  @Test
  public void testUnsupportedRequestEncodingOfUncompressedMessage() {
    Buffer request = Buffer.buffer(Request.newBuilder().setName("Julien").build().toByteArray());
    HttpClientResponse response = call(GrpcMediaType.GRPC, "snappy", Buffer.buffer().appendByte((byte) 0).appendInt(request.length()).appendBuffer(request));
    assertTrue("The message must be decoded", decoded.get());
    assertEquals("" + GrpcStatus.OK.code, response.getTrailer("grpc-status"));
  }

  @Test
  public void testCompressedFlagWithoutEncoding() {
    testCompressedFlag(null, "no grpc-encoding");
  }

  @Test
  public void testCompressedFlagWithIdentityEncoding() {
    testCompressedFlag("identity", "grpc-encoding identity");
  }

  private void testCompressedFlag(String encoding, String described) {
    HttpClientResponse response = call(GrpcMediaType.GRPC, encoding, compressed());
    assertFalse("The message must not be decoded", decoded.get());
    assertEquals("" + GrpcStatus.INTERNAL.code, response.getHeader(GrpcHeaderNames.GRPC_STATUS));
    String message = message(response);
    assertTrue(message, message.contains("compressed"));
    assertTrue(message, message.contains(described));
  }

  private static Buffer compressed() {
    Buffer payload = Buffer.buffer("compressed");
    return Buffer.buffer().appendByte((byte) 1).appendInt(payload.length()).appendBuffer(payload);
  }

  private HttpClientResponse call(CharSequence contentType, String encoding, Buffer frame) {
    startServer(GrpcServer.server(vertx).callHandler(UNARY, call -> call.handler(request -> {
      decoded.set(true);
      call.response().end(Reply.newBuilder().setMessage("Hello " + request.getName()).build());
    })));

    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false));

    try {
      HttpClientRequest request = client.request(HttpMethod.POST, port, "localhost", "/" + TestServiceGrpc.getUnaryMethod().getFullMethodName()).await();
      if (encoding != null) {
        request.putHeader(GrpcHeaderNames.GRPC_ENCODING, encoding);
      }

      request.putHeader(HttpHeaders.CONTENT_TYPE, contentType);

      HttpClientResponse response = request.send(frame).compose(resp -> resp.body().map(resp)).await();

      assertEquals(200, response.statusCode());
      return response;
    } finally {
      client.close().await();
    }
  }

  private static String message(HttpClientResponse response) {
    return URLDecoder.decode(response.getHeader(GrpcHeaderNames.GRPC_MESSAGE), StandardCharsets.UTF_8);
  }
}
