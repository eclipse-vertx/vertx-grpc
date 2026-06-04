package io.vertx.grpc.common.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.ProtobufJsonWriter;

public final class ProtobufJsonWriterImpl implements ProtobufJsonWriter {

  private final JsonFormat.Printer printer;

  public ProtobufJsonWriterImpl(JsonWireFormat format) {
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

  @Override
  public Buffer write(MessageOrBuilder message) throws CodecException {
    try {
      return Buffer.buffer(printer.print(message));
    } catch (InvalidProtocolBufferException e) {
      throw new CodecException(e);
    }
  }
}
