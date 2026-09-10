package io.vertx.grpc.common.impl;

import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import io.vertx.grpc.common.*;

import java.util.EnumSet;

public class DefaultMessageDecoder<T> implements JsonGrpcMessageDecoder<T> {

  private final EnumSet<WireFormat> formats;
  private final JsonReaderConfig jsonConfig;
  private final Parser<T> parser;
  private final Message dit;

  public DefaultMessageDecoder(JsonReaderConfig jsonConfig, EnumSet<WireFormat> formats, Parser<T> parser, Message dit) {
    this.jsonConfig = jsonConfig;
    this.formats = formats;
    this.parser = parser;
    this.dit = dit;
  }

  @Override
  public DefaultMessageDecoder<T> configure(JsonReaderConfig config) {
    return new DefaultMessageDecoder<>(config, formats, parser, dit);
  }

  @Override
  public T decode(GrpcMessage msg) throws CodecException {
    WireFormat format = msg.format();
    if (!formats.contains(format)) {
      throw new CodecException("Wire format not accepted: " + format);
    }
    switch (format) {
      case PROTOBUF:
        try {
          return parser.parseFrom(msg.payload().getBytes());
        } catch (InvalidProtocolBufferException e) {
          throw new CodecException(e);
        }
      case JSON:
        Message.Builder builder = dit.toBuilder();
        ProtobufJsonReader.create(jsonConfig).merge(msg.payload(), builder);
        return (T) builder.build();
      default:
        throw new IllegalArgumentException();
    }
  }

  @Override
  public boolean accepts(WireFormat format) {
    return formats.contains(format);
  }

  @Override
  public Descriptors.Descriptor messageDescriptor() {
    return dit.getDescriptorForType();
  }
}
