package io.vertx.grpc.server.auth;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.auth.impl.JWTGrpcClientImpl;

/**
 * A gRPC client for Vert.x which is supports JWT based authentication.
 *
 * @see GrpcClient for details on the general client usage.
 */
public interface JWTGrpcClient extends GrpcClient {

  /**
   * Create a new client
   * 
   * @param client the vertx instance
   * @return the created client
   */
  static JWTGrpcClient create(GrpcClient client) {
    return new JWTGrpcClientImpl(client);
  }

  /**
   * Create a new client
   * 
   * @param vertx the vertx instance
   * @return the created client
   */
  static JWTGrpcClient create(Vertx vertx) {
    return new JWTGrpcClientImpl(GrpcClient.client(vertx));
  }

  /**
   * Set the token credentials which will be send to the server.
   * 
   * @param credentials JWT information
   * @return Fluent API
   */
  JWTGrpcClient withCredentials(TokenCredentials credentials);
}
