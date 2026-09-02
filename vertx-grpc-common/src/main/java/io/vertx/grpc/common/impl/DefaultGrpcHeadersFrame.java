package io.vertx.grpc.common.impl;

import io.vertx.core.MultiMap;
import io.vertx.grpc.common.WireFormat;

import java.time.Duration;
import java.util.Objects;

public class DefaultGrpcHeadersFrame implements GrpcHeadersFrame {

  private final WireFormat format;
  private final String encoding;
  private final MultiMap metadata;
  private final Duration timeout;

  public DefaultGrpcHeadersFrame(WireFormat format, String encoding, MultiMap metadata) {
    this(format, encoding, metadata, null);
  }

  public DefaultGrpcHeadersFrame(WireFormat format, String encoding, MultiMap metadata, Duration timeout) {
    this.format = Objects.requireNonNull(format);
    this.encoding = encoding;
    this.metadata = metadata;
    this.timeout = timeout;
  }

  @Override
  public WireFormat format() {
    return format;
  }

  public String encoding() {
    return encoding;
  }

  public MultiMap metadata() {
    return metadata;
  }

  @Override
  public Duration timeout() {
    return timeout;
  }
}
