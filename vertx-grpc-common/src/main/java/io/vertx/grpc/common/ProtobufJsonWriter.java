package io.vertx.grpc.common;

import com.google.protobuf.MessageOrBuilder;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.impl.ProtobufJsonWriterImpl;

/**
 * Writes a protobuf {@link MessageOrBuilder} as a JSON {@link Buffer}.
 * <p>
 * Use {@link #create(JsonWireFormat.WriterConfig)} to get an instance. The default
 * implementation is backed by {@code com.google.protobuf.util.JsonFormat}.
 */
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface ProtobufJsonWriter {

  /**
   * @return a writer configured from {@code config}
   */
  static ProtobufJsonWriter create(JsonWireFormat.WriterConfig config) {
    return new ProtobufJsonWriterImpl(config);
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
