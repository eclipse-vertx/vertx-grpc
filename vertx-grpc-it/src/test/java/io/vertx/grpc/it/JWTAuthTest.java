package io.vertx.grpc.it;

import org.junit.Test;

import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.GrpcException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.ServerTestBase;
import io.vertx.grpc.server.auth.JWTGrpcClient;
import io.vertx.grpc.server.auth.JWTGrpcServer;

public class JWTAuthTest extends ServerTestBase {

  private String validToken;
  private JWTGrpcServer jwtServer;
  private String expiredToken;
  private static final String BROKEN_TOKEN = "this-token-value-is-bogus";
  private static final String INVALID_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2hhbm5lcyIsImlhdCI6MTY3ODgwNDgwN30";

  public void setupClientServer(TestContext should, boolean expectUser) {

    // Prepare JWT auth and generate token to be used for the client
    JWTAuthOptions config = new JWTAuthOptions()
      .setKeyStore(new KeyStoreOptions()
        .setPath("keystore.jceks")
        .setPassword("secret")
        .setType("jceks"));
    JWTAuth authProvider = JWTAuth.create(vertx, config);
    validToken = authProvider.generateToken(new JsonObject().put("sub", "johannes"), new JWTOptions().setIgnoreExpiration(true));
    expiredToken = authProvider.generateToken(new JsonObject().put("sub", "johannes"), new JWTOptions().setExpiresInSeconds(1));

    jwtServer = JWTGrpcServer.create(vertx, authProvider);

    jwtServer.callHandler(GreeterGrpc.getSayHelloMethod(), true, request -> {
      request.handler(hello -> {
        GrpcServerResponse<HelloRequest, HelloReply> response = request.response();
        HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + hello.getName()).build();
        response.end(reply);
        User user = request.user();
        if (expectUser) {
          should.assertNotNull(user);
          should.assertEquals("johannes", user.subject());
        } else {
          should.assertNull(user);
        }
      }).errorHandler(error -> {
        should.fail("Error should not happen " + error);
      });
    });

    startServer(jwtServer);
  }

  @Test
  public void testJWTBrokenTokenAuthentication(TestContext should) {
    setupClientServer(should, false);

    JWTGrpcClient jwtClient = JWTGrpcClient.create(vertx).withCredentials(new TokenCredentials(BROKEN_TOKEN));
    Future<HelloReply> clientReply = sayHello(jwtClient);

    Async test = should.async();
    clientReply
      .onFailure(error -> {
        if (error instanceof GrpcException) {
          GrpcException grpcError = (GrpcException) error;
          should.assertEquals(GrpcStatus.UNAUTHENTICATED, grpcError.status(), "The status code did not match. Got: " + grpcError.status().name());
          should.assertNotNull(grpcError.response());
        } else {
          should.fail(error);
        }
        test.complete();
      })
      .onSuccess(reply -> {
        should.fail("The test should fail with an error due to the usage of the invalid token");
      });
  }

  @Test
  public void testJWTExpiredTokenAuthentication(TestContext should) {
    setupClientServer(should, false);
    // Let the token expire
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    JWTGrpcClient jwtClient = JWTGrpcClient.create(vertx).withCredentials(new TokenCredentials(expiredToken));
    Future<HelloReply> clientReply = sayHello(jwtClient);

    Async test = should.async();
    clientReply
      .onFailure(error -> {
        if (error instanceof GrpcException) {
          GrpcException grpcError = (GrpcException) error;
          should.assertEquals(GrpcStatus.UNAUTHENTICATED, grpcError.status(), "The status code did not match. Got: " + grpcError.status().name());
          should.assertNotNull(grpcError.response());
        } else {
          should.fail(error);
        }
        test.complete();
      })
      .onSuccess(reply -> {
        should.fail("The test should fail with an error due to the usage of the invalid token");
      });
  }

  @Test
  public void testJWTValidAuthentication(TestContext should) {
    setupClientServer(should, true);

    JWTGrpcClient jwtClient = JWTGrpcClient.create(vertx).withCredentials(new TokenCredentials(validToken));
    Future<HelloReply> clientReply = sayHello(jwtClient);

    Async test = should.async();
    clientReply
      .onFailure(should::fail)
      .onSuccess(reply -> {
        System.out.println("Reply: " + reply.getMessage());
        test.complete();
      });

  }

  private Future<HelloReply> sayHello(JWTGrpcClient jwtClient) {
    return jwtClient
      .request(SocketAddress.inetSocketAddress(8080, "localhost"), GreeterGrpc.getSayHelloMethod())
      .compose(request -> {
        request.end(HelloRequest
          .newBuilder()
          .setName("Johannes")
          .build());

        return request.response()
          .compose(GrpcClientResponse::last);
      });
  }
}
