package io.vertx.grpc.common.impl;

import com.google.protobuf.Message;
import io.vertx.grpc.common.*;

import java.util.function.Supplier;

public class DefaultJsonMessageDecoder<T> implements JsonGrpcMessageDecoder<T> {

  private final JsonReaderConfig jsonConfig;
  private final Supplier<Message.Builder> builder;

  public DefaultJsonMessageDecoder(JsonReaderConfig jsonConfig, Supplier<Message.Builder> builder) {
    this.jsonConfig = jsonConfig;
    this.builder = builder;
  }

  @Override
  public DefaultJsonMessageDecoder<T> configure(JsonReaderConfig config) {
    return new DefaultJsonMessageDecoder<>(config, builder);
  }

  @Override
  public T decode(GrpcMessage msg) throws CodecException {
    Message.Builder builderInstance = builder.get();
    ProtobufJsonReader.create(jsonConfig).merge(msg.payload(), builderInstance);
    return (T) builderInstance.build();
  }

  @Override
  public boolean accepts(WireFormat format) {
    return format == WireFormat.JSON;
  }
}
