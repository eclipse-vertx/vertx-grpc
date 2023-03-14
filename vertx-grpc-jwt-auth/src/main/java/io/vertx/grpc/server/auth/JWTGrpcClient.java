package io.vertx.grpc.server.auth;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.auth.impl.JWTGrpcClientImpl;

public interface JWTGrpcClient extends GrpcClient {

  JWTGrpcClient withCredentials(TokenCredentials credentials);

  static JWTGrpcClient create(GrpcClient client) {
    return new JWTGrpcClientImpl(client);
  }

  static JWTGrpcClient create(Vertx vertx) {
    return new JWTGrpcClientImpl(GrpcClient.client(vertx));
  }

}
