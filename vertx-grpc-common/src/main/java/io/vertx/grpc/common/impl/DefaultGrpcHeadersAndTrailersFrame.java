package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcStatus;

import java.time.Duration;

public class DefaultGrpcHeadersAndTrailersFrame implements GrpcHeadersFrame, GrpcTrailersFrame {

  private final String contentType;
  private final String encoding;
  private final MultiMap headers;
  private final GrpcStatus status;
  private final String statusMessage;
  private final MultiMap trailers;

  public DefaultGrpcHeadersAndTrailersFrame(String contentType, String encoding, MultiMap headers, GrpcStatus status, String statusMessage, MultiMap trailers) {
    this.contentType = contentType;
    this.encoding = encoding;
    this.headers = headers;
    this.status = status;
    this.statusMessage = statusMessage;
    this.trailers = trailers;
  }

  @Override
  public String contentType() {
    return contentType;
  }

  @Override
  public String encoding() {
    return encoding;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public GrpcStatus status() {
    return status;
  }

  @Override
  public String statusMessage() {
    return statusMessage;
  }

  @Override
  public MultiMap trailers() {
    return trailers;
  }

  @Override
  public Duration timeout() {
    return null;
  }
}
