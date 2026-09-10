package io.vertx.grpc.common.impl;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.grpc.common.*;

import java.util.EnumSet;

public class DefaultMessageEncoder<T extends MessageLite> implements JsonGrpcMessageEncoder<T> {

  private final JsonWriterConfig jsonConfig;
  private final EnumSet<WireFormat> formats;

  public DefaultMessageEncoder(JsonWriterConfig jsonConfig, EnumSet<WireFormat> formats) {
    this.jsonConfig = jsonConfig;
    this.formats = formats;
  }

  @Override
  public DefaultMessageEncoder<T> configure(JsonWriterConfig config) {
    return new DefaultMessageEncoder<>(config, formats);
  }

  @Override
  public GrpcMessage encode(T msg, WireFormat format) throws CodecException {
    if (!formats.contains(format)) {
      throw new CodecException("Wire format not accepted: " + format);
    }
    switch (format) {
      case PROTOBUF:
        byte[] bytes = msg.toByteArray();
        return GrpcMessage.message("identity", format, Buffer.buffer(bytes));
      case JSON:
        if (msg instanceof MessageOrBuilder) {
          return GrpcMessage.message("identity", format, ProtobufJsonWriter.create(jsonConfig).write((MessageOrBuilder) msg));
        }
        return GrpcMessage.message("identity", format, Json.encodeToBuffer(msg));
      default:
        throw new AssertionError();
    }
  }

  @Override
  public boolean accepts(WireFormat format) {
    return formats.contains(format);
  }
}
