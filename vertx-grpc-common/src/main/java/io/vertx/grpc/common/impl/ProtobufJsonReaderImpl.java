package io.vertx.grpc.common.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.ProtobufJsonReader;

import java.nio.charset.StandardCharsets;

public final class ProtobufJsonReaderImpl implements ProtobufJsonReader {

  private final JsonFormat.Parser parser;

  public ProtobufJsonReaderImpl(JsonWireFormat format) {
    JsonFormat.Parser parser = JsonFormat.parser();
    if (format.getIgnoringUnknownFields()) {
      parser = parser.ignoringUnknownFields();
    }
    this.parser = parser;
  }

  @Override
  public void merge(Buffer payload, Message.Builder builder) throws CodecException {
    try {
      parser.merge(payload.toString(StandardCharsets.UTF_8), builder);
    } catch (InvalidProtocolBufferException e) {
      throw new CodecException(e);
    }
  }
}
