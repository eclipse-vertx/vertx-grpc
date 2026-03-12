package io.vertx.grpc.common.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.WireFormat;

import java.util.function.Function;

public class GrpcTransformedMessage implements GrpcMessage {

  private final GrpcMessage wrapped;
  private final String encoding;
  private final Function<Buffer, Buffer> transformation;

  public GrpcTransformedMessage(GrpcMessage wrapped, String encoding, Function<Buffer, Buffer> transformation) {
    this.wrapped = wrapped;
    this.encoding = encoding;
    this.transformation = transformation;
  }

  @Override
  public String encoding() {
    return encoding;
  }

  @Override
  public WireFormat format() {
    return wrapped.format();
  }

  @Override
  public Buffer payload() {
    return transformation.apply(wrapped.payload());
  }
}
