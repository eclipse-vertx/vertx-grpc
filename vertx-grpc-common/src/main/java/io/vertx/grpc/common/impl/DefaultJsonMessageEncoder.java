package io.vertx.grpc.common.impl;

import com.google.protobuf.MessageOrBuilder;
import io.vertx.core.json.Json;
import io.vertx.grpc.common.*;

public class DefaultJsonMessageEncoder<T> implements JsonGrpcMessageEncoder<T> {

  private final JsonWriterConfig jsonConfig;

  public DefaultJsonMessageEncoder(JsonWriterConfig jsonConfig) {
    this.jsonConfig = jsonConfig;
  }

  @Override
  public DefaultJsonMessageEncoder<T> configure(JsonWriterConfig config) {
    return new DefaultJsonMessageEncoder<>(config);
  }

  @Override
  public GrpcMessage encode(T msg, WireFormat format) throws CodecException {
    if (msg instanceof MessageOrBuilder) {
      return GrpcMessage.message("identity", format, ProtobufJsonWriter.create(jsonConfig).write((MessageOrBuilder) msg));
    }
    return GrpcMessage.message("identity", format, Json.encodeToBuffer(msg));
  }

  @Override
  public boolean accepts(WireFormat format) {
    return format == WireFormat.JSON;
  }
}
