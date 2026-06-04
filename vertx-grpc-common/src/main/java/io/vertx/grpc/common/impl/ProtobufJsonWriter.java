package io.vertx.grpc.common.impl;

import com.google.protobuf.MessageOrBuilder;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.JsonWireFormat;

/**
 * Writes a protobuf {@link MessageOrBuilder} as a JSON {@link Buffer}.
 * <p>
 * Use {@link #create(JsonWireFormat)} to get an instance. The default implementation is backed
 * by {@code com.google.protobuf.util.JsonFormat}.
 */
public interface ProtobufJsonWriter {

  /**
   * @return a writer configured from {@code format}
   */
  static ProtobufJsonWriter create(JsonWireFormat format) {
    return new ProtobufJsonWriterImpl(format);
  }

  /**
   * Encode {@code message} as JSON.
   *
   * @param message the protobuf message to encode
   * @return the encoded JSON payload
   * @throws CodecException when the message cannot be encoded
   */
  Buffer write(MessageOrBuilder message) throws CodecException;
}
