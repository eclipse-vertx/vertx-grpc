package io.vertx.grpc.client.tests;

import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * The client side of the test cases of https://github.com/grpc/grpc/blob/master/doc/compression.md
 */
public class CompressionSpecTest extends ClientTestBase {

  @Test
  public void testUnsupportedResponseEncoding() throws Exception {
    testCall("snappy", "snappy", "identity", "gzip");
  }

  @Test
  public void testUnsupportedResponseEncodingOfUncompressedMessage() throws Exception {
    Buffer reply = Buffer.buffer(Reply.newBuilder().setMessage("Hello Julien").build().toByteArray());

    vertx.createHttpServer().requestHandler(req -> req.body().onSuccess(body -> {
      req.response().putHeader(HttpHeaders.CONTENT_TYPE, GrpcMediaType.GRPC);
      req.response().putHeader(GrpcHeaderNames.GRPC_ENCODING, "snappy");
      req.response().write(Buffer.buffer().appendByte((byte) 0).appendInt(reply.length()).appendBuffer(reply));
      req.response().putTrailer(GrpcHeaderNames.GRPC_STATUS, "" + GrpcStatus.OK.code);
      req.response().end();
    })).listen(port, "localhost").await();

    GrpcClient client = GrpcClient.client(vertx);

    try {
      GrpcClientRequest<Request, Reply> request = client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY).await();
      request.end(Request.newBuilder().setName("Julien").build());
      GrpcClientResponse<Request, Reply> response = request.response().await();

      assertEquals("Hello Julien", response.last().await().getMessage());
      assertEquals(GrpcStatus.OK, response.status());
    } finally {
      client.close().await();
    }
  }

  @Test
  public void testCompressedFlagWithoutEncoding() throws Exception {
    testCall(null, "compressed", "no grpc-encoding");
  }

  @Test
  public void testCompressedFlagWithIdentityEncoding() throws Exception {
    testCall("identity", "compressed", "grpc-encoding identity");
  }

  private void testCall(String encoding, String... described) throws Exception {
    Promise<Void> reset = Promise.promise();
    Buffer payload = Buffer.buffer("compressed");

    vertx.createHttpServer().requestHandler(req -> req.body().onSuccess(body -> {
      req.response().exceptionHandler(err -> {
        if (err instanceof StreamResetException) {
          reset.tryComplete();
        }
      });
      req.response().putHeader(HttpHeaders.CONTENT_TYPE, GrpcMediaType.GRPC);
      if (encoding != null) {
        req.response().putHeader(GrpcHeaderNames.GRPC_ENCODING, encoding);
      }
      req.response().write(Buffer.buffer().appendByte((byte) 1).appendInt(payload.length()).appendBuffer(payload));
    })).listen(port, "localhost").await();

    AtomicBoolean decoded = new AtomicBoolean();
    GrpcClient client = GrpcClient.client(vertx);

    try {
      GrpcClientRequest<Request, Reply> request = client.request(SocketAddress.inetSocketAddress(port, "localhost"), UNARY).await();
      request.end(Request.newBuilder().setName("Julien").build());

      GrpcClientResponse<Request, Reply> response = request.response().await();
      response.handler(reply -> decoded.set(true));

      InvalidStatusException failure = assertThrows(InvalidStatusException.class, () -> response.end().await());

      assertEquals(GrpcStatus.INTERNAL, failure.actualStatus());
      assertEquals(GrpcStatus.INTERNAL, response.status());

      for (String part : described) {
        assertTrue(response.statusMessage(), response.statusMessage().contains(part));
      }

      reset.future().await();
      assertFalse("The message must not be decoded", decoded.get());
    } finally {
      client.close().await();
    }
  }
}
