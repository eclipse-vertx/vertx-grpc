package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;

import java.time.Duration;
import java.util.Objects;

public class DefaultGrpcHeadersFrame implements GrpcHeadersFrame {

  private final String contentType;
  private final String encoding;
  private final MultiMap headers;
  private final Duration timeout;

  public DefaultGrpcHeadersFrame(String contentType, String encoding, MultiMap headers) {
    this(contentType, encoding, headers, null);
  }

  public DefaultGrpcHeadersFrame(String contentType, String encoding, MultiMap headers, Duration timeout) {
    this.contentType = Objects.requireNonNull(contentType);
    this.encoding = encoding;
    this.headers = headers;
    this.timeout = timeout;
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

  @Override
  public Duration timeout() {
    return timeout;
  }
}
