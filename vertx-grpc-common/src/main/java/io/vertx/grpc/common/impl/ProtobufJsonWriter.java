package io.vertx.grpc.common.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.JsonWireFormat;

/**
 * Writes a protobuf {@link MessageOrBuilder} as a JSON {@link Buffer}.
 * <p>
 * Use {@link #create(JsonWireFormat)} to get an instance, backed by
 * {@code com.google.protobuf.util.JsonFormat}.
 */
public final class ProtobufJsonWriter {

  /**
   * @return a writer configured from {@code format}
   */
  public static ProtobufJsonWriter create(JsonWireFormat format) {
    return new ProtobufJsonWriter(format);
  }

  private final JsonFormat.Printer printer;

  private ProtobufJsonWriter(JsonWireFormat format) {
    JsonFormat.Printer printer = JsonFormat.printer();
    if (format.alwaysPrintFieldsWithNoPresence()) {
      printer = printer.alwaysPrintFieldsWithNoPresence();
    }
    if (format.omittingInsignificantWhitespace()) {
      printer = printer.omittingInsignificantWhitespace();
    }
    if (format.preservingProtoFieldNames()) {
      printer = printer.preservingProtoFieldNames();
    }
    if (format.printingEnumsAsInts()) {
      printer = printer.printingEnumsAsInts();
    }
    if (format.sortingMapKeys()) {
      printer = printer.sortingMapKeys();
    }
    this.printer = printer;
  }

  /**
   * Encode {@code message} as JSON.
   *
   * @param message the protobuf message to encode
   * @return the encoded JSON payload
   * @throws CodecException when the message cannot be encoded
   */
  public Buffer write(MessageOrBuilder message) throws CodecException {
    try {
      return Buffer.buffer(printer.print(message));
    } catch (InvalidProtocolBufferException e) {
      throw new CodecException(e);
    }
  }
}
