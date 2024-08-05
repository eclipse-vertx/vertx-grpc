package io.vertx.grpc.server;

public enum GrpcProtocol {

  HTTP_2("application/grpc", false), WEB("application/grpc-web", true), WEB_TEXT("application/grpc-web-text", true);

  private final String mediaType;
  private final boolean web;

  GrpcProtocol(String mediaType, boolean web) {
    this.mediaType = mediaType;
    this.web = web;
  }

  public boolean isWeb() {
    return web;
  }

  public String mediaType() {
    return mediaType;
  }
}
