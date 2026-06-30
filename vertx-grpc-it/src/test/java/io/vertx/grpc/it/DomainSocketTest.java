package io.vertx.grpc.it;

import io.grpc.examples.helloworld.GreeterGrpcClient;
import io.grpc.examples.helloworld.GreeterGrpcService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class DomainSocketTest extends GrpcTestBase {

  @Test
  public void testPlainDomainSocket(TestContext ctx) throws Exception {
    testDomainSocket(ctx, false);
  }

  @Test
  public void testTlsDomainSocket(TestContext ctx) throws Exception {
    testDomainSocket(ctx, true);
  }

  private void testDomainSocket(TestContext ctx, boolean ssl) throws Exception {
    Assume.assumeTrue(PlatformDependent.javaVersion() >= 16);
    File sockFile = TestUtils.tmpFile(".sock");
    SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
    HttpServerOptions serverOptions = new HttpServerOptions()
      .setUseAlpn(true)
      .setSsl(ssl)
      .setKeyCertOptions(Cert.SERVER_JKS.get());
    vertx
      .createHttpServer(serverOptions)
      .requestHandler(GrpcServer
        .server(vertx)
        .callHandler(GreeterGrpcService.SayHello, request -> {
          HttpConnection connection = request.connection();
          ctx.assertEquals(ssl, connection.isSsl());
          ctx.assertTrue(connection.remoteAddress().isDomainSocket());
          if (ssl) {
            ctx.assertNull(connection.sslSession().getPeerHost());
          }
          request.handler(hello -> {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + hello.getName()).build();
            request.response().end(reply);
          });
        }))
      .listen(sockAddress)
      .await();
    GrpcClient client;
    if (ssl) {
      // Disable hostname verification because there is no host:port to verify the server validity against
      client = GrpcClient.client(vertx, new ClientSSLOptions()
        .setTrustAll(true)
        .setHostnameVerificationAlgorithm(""));
    } else {
      client = GrpcClient.client(vertx);
    }
    GreeterGrpcClient greeterClient = GreeterGrpcClient.create(client, sockAddress);
    HelloReply reply = greeterClient.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await();
    assertEquals("Hello Julien", reply.getMessage());
  }
}
