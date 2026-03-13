package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;

public class DefaultGrpcHeadersFrame implements GrpcHeadersFrame {

  private final String contentType;
  private final String encoding;
  private final MultiMap headers;

  public DefaultGrpcHeadersFrame(String contentType, String encoding, MultiMap headers) {
    this.contentType = contentType;
    this.encoding = encoding;
    this.headers = headers;
  }

  public String contentType() {
    return contentType;
  }

  public String encoding() {
    return encoding;
  }

  public MultiMap headers() {
    return headers;
  }
}
