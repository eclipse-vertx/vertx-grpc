package io.vertx.grpc.common;

import com.google.protobuf.Message;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.impl.ProtobufJsonReaderImpl;

/**
 * Reads JSON {@link Buffer} payloads into a protobuf {@link Message.Builder}.
 * <p>
 * Use {@link #create(JsonWireFormat)} to get an instance. The default implementation is backed
 * by {@code com.google.protobuf.util.JsonFormat}.
 */
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface ProtobufJsonReader {

  /**
   * @return a reader configured from {@code format}
   */
  static ProtobufJsonReader create(JsonWireFormat format) {
    return new ProtobufJsonReaderImpl(format);
  }

  /**
   * Merge the JSON {@code payload} into the supplied {@code builder}.
   *
   * @param payload the JSON payload to decode
   * @param builder the target builder
   * @throws CodecException when the payload cannot be parsed
   */
  void merge(Buffer payload, Message.Builder builder) throws CodecException;
}
