package io.vertx.grpc.common.proto;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.grpc.common.proto.schema.Field;
import io.vertx.grpc.common.proto.schema.MessageType;
import io.vertx.grpc.common.proto.schema.ScalarType;

public class ProtobufReader {

  private static void parseFixed(ProtoDecoder decoder, Field field, Visitor visitor) {
    ScalarType bt = (ScalarType) field.type;
    switch (bt.id()) {
      case DOUBLE:
        assertTrue(decoder.readDouble());
        double d = decoder.doubleValue();
        visitor.visitDouble(field, d);
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

  private static void parseVarInt(ProtoDecoder decoder, Field field, Visitor visitor) {
    assertTrue(decoder.readVarInt());
    int value = decoder.int32Value();
    switch (field.type.id()) {
      case ENUM:
        visitor.visitVarInt32(field, value);
        break;
      case BOOL:
        visitor.visitVarInt32(field, value);
        break;
      default:
        throw new UnsupportedOperationException("" + field.type);
    }
  }

  private static void parseLen(ProtoDecoder decoder, Field field, Visitor visitor) {
    assertTrue(decoder.readVarInt());
    int len = decoder.int32Value();
    if (field.type instanceof MessageType) {
      int to = decoder.len();
      decoder.len(decoder.index() + len);
      visitor.enter(field);
      parse(decoder, (MessageType) field.type, visitor);
      visitor.leave(field);
      decoder.len(to);
    } else {
      ScalarType builtInType = (ScalarType) field.type;
      switch (builtInType.id()) {
        case STRING:
          String s = decoder.readString(len);
          visitor.visitString(field, s);
          break;
        default:
          throw new UnsupportedOperationException("" + field.type);
      }
    }
  }

  public static void parse(MessageType rootType, Visitor visitor, Buffer buffer) {
    ProtoDecoder decoder = new ProtoDecoder(buffer);
    visitor.init(rootType);
    parse(decoder, rootType, visitor);
  }

  private static void parse(ProtoDecoder decoder, MessageType type, Visitor visitor) {
    while (decoder.isReadable()) {
      assertTrue(decoder.readTag());
      int fieldNumber  = decoder.fieldNumber();
      Field field = type.field(fieldNumber);
      if (field == null) {
        throw new DecodeException();
      }
      switch (field.type.wireType()) {
        case LEN:
          parseLen(decoder, field, visitor);
          break;
        case I64:
          parseFixed(decoder, field, visitor);
          break;
        case VARINT:
          parseVarInt(decoder, field, visitor);
          break;
        default:
          throw new UnsupportedOperationException("Implement me " + field.type.wireType());
      }
    }
  }

  private static void assertTrue(boolean cond) {
    if (!cond) {
      throw new DecodeException();
    }
  }
}
