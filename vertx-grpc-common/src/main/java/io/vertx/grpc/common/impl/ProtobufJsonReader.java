package io.vertx.grpc.common.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.JsonWireFormat;

import java.nio.charset.StandardCharsets;

/**
 * Reads JSON {@link Buffer} payloads into a protobuf {@link Message.Builder}.
 * <p>
 * Use {@link #create(JsonWireFormat)} to get an instance, backed by
 * {@code com.google.protobuf.util.JsonFormat}.
 */
public final class ProtobufJsonReader {

  /**
   * @return a reader configured from {@code format}
   */
  public static ProtobufJsonReader create(JsonWireFormat format) {
    return new ProtobufJsonReader(format);
  }

  private final JsonFormat.Parser parser;

  private ProtobufJsonReader(JsonWireFormat format) {
    JsonFormat.Parser parser = JsonFormat.parser();
    if (format.ignoringUnknownFields()) {
      parser = parser.ignoringUnknownFields();
    }
    this.parser = parser;
  }

  /**
   * Merge the JSON {@code payload} into the supplied {@code builder}.
   *
   * @param payload the JSON payload to decode
   * @param builder the target builder
   * @throws CodecException when the payload cannot be parsed
   */
  public void merge(Buffer payload, Message.Builder builder) throws CodecException {
    try {
      parser.merge(payload.toString(StandardCharsets.UTF_8), builder);
    } catch (InvalidProtocolBufferException e) {
      throw new CodecException(e);
    }
  }
}
