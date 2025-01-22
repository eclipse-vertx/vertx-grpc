package io.vertx.grpc.server;

public enum GrpcProtocol {

  HTTP_2("application/grpc", false),
  HTTP_1("application/json", false, true),
  WEB("application/grpc-web", true),
  WEB_TEXT("application/grpc-web-text", true);

  private final String mediaType;
  private final boolean web;
  private final boolean text;

  GrpcProtocol(String mediaType, boolean web) {
    this(mediaType, web, false);
  }

  GrpcProtocol(String mediaType, boolean web, boolean text) {
    this.mediaType = mediaType;
    this.web = web;
    this.text = text;
  }

  public boolean isWeb() {
    return web;
  }

  public boolean isText() {
    return text;
  }

  public String mediaType() {
    return mediaType;
  }
}
