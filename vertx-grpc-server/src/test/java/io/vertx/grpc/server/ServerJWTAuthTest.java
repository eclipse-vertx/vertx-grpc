package io.vertx.grpc.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.google.common.net.HttpHeaders;

import examples.GreeterGrpc;
import examples.GreeterGrpc.GreeterBlockingStub;
import examples.HelloReply;
import examples.HelloRequest;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.server.auth.GrpcAuthenticationHandler;
import io.vertx.grpc.server.auth.GrpcJWTAuthenticationHandler;

public class ServerJWTAuthTest extends ServerTestBase {

  private String validToken;
  private GrpcServer jwtServer;
  private String expiredToken;
  private static final String BROKEN_TOKEN = "this-token-value-is-bogus";
  private static final String INVALID_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2hhbm5lcyIsImlhdCI6MTY3ODgwNDgwN30";
  private static final String NO_TOKEN = null;

  public void setupClientServer(TestContext should, boolean expectUser) {

    // Prepare JWT auth and generate token to be used for the client
    JWTAuthOptions config = new JWTAuthOptions()
      .setKeyStore(new KeyStoreOptions()
        .setPath("keystore.jceks")
        .setPassword("secret")
        .setType("jceks"));

    JWTAuth authProvider = JWTAuth.create(vertx, config);
    GrpcAuthenticationHandler authHandler = GrpcJWTAuthenticationHandler.create(authProvider, "");
    validToken = authProvider.generateToken(new JsonObject().put("sub", "johannes"), new JWTOptions().setIgnoreExpiration(true));
    expiredToken = authProvider.generateToken(new JsonObject().put("sub", "johannes"), new JWTOptions().setExpiresInSeconds(1));

    jwtServer = GrpcServer.server(vertx);

    // Register the secured
    jwtServer.callHandler(authHandler, GreeterGrpc.getSaySecuredHelloMethod(), request -> {
      handleHelloRequest(should, request, expectUser);
    });
    // And public handler
    jwtServer.callHandler(GreeterGrpc.getSayHelloMethod(), request -> {
      handleHelloRequest(should, request, expectUser);
    });

    startServer(jwtServer);
  }

  private void handleHelloRequest(TestContext should, GrpcServerRequest<HelloRequest, HelloReply> request, boolean expectUser) {
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
  }

  /**
   * Invoke request with a broken JWT in the headers. Request should be rejected by auth handler.
   * @param should
   */
  @Test
  public void testJWTBrokenTokenAuthentication(TestContext should) {
    setupClientServer(should, false);
    StatusRuntimeException error = invokeSecuredRequest(should, BROKEN_TOKEN);
    assertEquals("The token should not have been accepted", Status.UNAUTHENTICATED, error.getStatus());
  }

  /**
   * Invoke request with expired JWT. Request should be rejected.
   * @param should
   */
  @Test
  public void testJWTExpiredTokenAuthentication(TestContext should) {
    setupClientServer(should, false);
    // Let the token expire
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    StatusRuntimeException error = invokeSecuredRequest(should, expiredToken);
    assertEquals("The request should have failed since the token is expired", Status.UNAUTHENTICATED, error.getStatus());

  }

  /**
   * Invoke request with valid JWT. Request should pass and user should be accessible.
   * @param should
   */
  @Test
  public void testJWTValidAuthentication(TestContext should) {
    setupClientServer(should, true);
    assertNull("No error should occur", invokeSecuredRequest(should, validToken));
  }

  /**
   * Invoke request to the public method with no JWT provided.
   * Request should pass.
   * @param should
   */
  @Test
  public void testNoJWTValidAuthentication(TestContext should) {
    setupClientServer(should, false);
    assertNull("No error should occur", invokePublicRequest(should, NO_TOKEN));
  }

  /**
   * Invoke request with invalid token. Request should fail.
   * @param should
   */
  @Test
  public void testJWTInvalidAuthentication(TestContext should) {
    setupClientServer(should, true);
    StatusRuntimeException error = invokeSecuredRequest(should, INVALID_TOKEN);
    assertEquals("The invalid token should have been rejected.", Status.UNAUTHENTICATED, error.getStatus());
  }

  private StatusRuntimeException invokePublicRequest(TestContext should, String token) {
    return invokeRequest(should, token, false);
  }

  private StatusRuntimeException invokeSecuredRequest(TestContext should, String token) {
    return invokeRequest(should, token, true);
  }

  private StatusRuntimeException invokeRequest(TestContext should, String token, boolean secured) {
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

    GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
    if (secured && token != null) {
      Metadata header = new Metadata();
      header.put(Metadata.Key.of(HttpHeaders.AUTHORIZATION, Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
      stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));
    }

    try {
      HelloRequest request = HelloRequest.newBuilder().setName("Johannes").build();
      HelloReply res;
      if (secured) {
        res = stub.saySecuredHello(request);
      } else {
        res = stub.sayHello(request);
      }

      should.assertEquals("Hello Johannes", res.getMessage());
      return null;
    } catch (StatusRuntimeException e) {
      return e;
    }

  }

}
