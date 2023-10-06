package examples;

import io.vertx.core.Vertx;
import io.vertx.docgen.Source;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.grpc.server.auth.jwt.JWTGrpcClient;

@Source
public class JWTGrpcClientExamples {

  private String TOKEN;

  public void createClient(Vertx vertx) {
    JWTGrpcClient jwtClient = JWTGrpcClient.create(vertx)
      .withCredentials(new TokenCredentials(TOKEN));
  }

}
