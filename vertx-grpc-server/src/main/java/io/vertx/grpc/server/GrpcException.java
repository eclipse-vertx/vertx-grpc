package io.vertx.grpc.server;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.grpc.common.GrpcStatus;

public class GrpcException extends RuntimeException {

  private static final long serialVersionUID = -7838327176604697641L;

  private GrpcStatus status;

  private HttpClientResponse httpResponse;

  public GrpcException(String msg, GrpcStatus status,
    HttpClientResponse httpResponse) {
    super(msg);
    this.status = status;
    this.httpResponse = httpResponse;
  }

  public GrpcException(GrpcStatus status) {
    this.status = status;
  }

  public GrpcException(GrpcStatus status, Throwable err) {
    super(err);
    this.status = status;
  }

  public GrpcException(GrpcStatus status, String msg) {
    super(msg);
    this.status = status;
  }

  public GrpcStatus status() {
    return status;
  }

  public HttpClientResponse response() {
    return httpResponse;
  }
}
