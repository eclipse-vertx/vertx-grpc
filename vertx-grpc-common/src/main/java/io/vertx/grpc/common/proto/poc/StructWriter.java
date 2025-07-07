package io.vertx.grpc.common.proto.poc;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.ProtoEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class StructWriter {

  public static Buffer toProto(JsonObject struct) {
    Buffer ret = Buffer.buffer();
    ProtoEncoder encoder = new ProtoEncoder(ret);
    writeStruct(struct, encoder);
    return ret;
  }

  public static void writeStruct(JsonObject struct, ProtoEncoder encoder) {
    writeStruct(struct.getMap(), encoder);
  }

  public static void writeStruct(Map<String, Object> struct, ProtoEncoder encoder) {
    for (Map.Entry<String, Object> entry : struct.entrySet()) {
      writeStructField(entry, encoder);
    }
  }

  public static void writeList(JsonArray list, ProtoEncoder encoder) {
    writeList(list.getList(), encoder);
  }

  public static void writeList(List<Object> list, ProtoEncoder encoder) {
    for (Object value : list) {
      encoder.writeTag(1, 2);
      encoder.writeVarInt32(sizeOfValue(value));
      writeValue(value, encoder);
    }
  }

  private static void writeStructField(Map.Entry<String, ?> entry, ProtoEncoder encoder) {
    // Compute key length
    String key = entry.getKey();
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

    Object value = entry.getValue();
    int valueLength = sizeOfValue(value);

    // fields field
    encoder.writeTag(1, 2);
    encoder.writeVarInt32(
      1 + ProtoEncoder.computeRawVarint32Size(keyBytes.length) + keyBytes.length +
      1 + ProtoEncoder.computeRawVarint32Size(valueLength) + valueLength
      );

    // key field
    encoder.writeTag(1, 2);
    encoder.writeVarInt32(keyBytes.length);
    encoder.writeBytes(keyBytes);

    // value field
    encoder.writeTag(2, 2);
    encoder.writeVarInt32(valueLength);
    writeValue(value, encoder);
  }

  private static void writeValue(Object value, ProtoEncoder encoder) {
    if (value instanceof String) {
      String string = (String) value;
      encoder.writeTag(3, 2);
      encoder.writeVarInt32(sizeOfString(string));
      encoder.writeBytes(string.getBytes(StandardCharsets.UTF_8));
    } else if (value instanceof JsonObject) {
      writeValue(((JsonObject) value).getMap(), encoder);
    } else if (value instanceof Map<?, ?>) {
      Map<String, Object> struct = (Map<String, Object>) value;
      encoder.writeTag(5, 2);
      encoder.writeVarInt32(sizeOfStruct(struct));
      writeStruct(struct, encoder);
    } else if (value instanceof JsonArray) {
      writeValue(((JsonArray)value).getList(), encoder);
    } else if (value instanceof List) {
      List list = (List) value;
      encoder.writeTag(6, 2);
      encoder.writeVarInt32(sizeOfList(list));
      writeList(list, encoder);
    } else if (value instanceof Number) {
      Number number = (Number)value;
      double d = number.doubleValue();
      encoder.writeTag(2, 1);
      encoder.writeDouble(d);
    } else if (value == null) {
      encoder.writeTag(1, 0);
      encoder.writeVarInt32(0);
    } else if (value instanceof Boolean) {
      boolean b = (Boolean) value;
      encoder.writeTag(4, 0);
      encoder.writeVarInt32(b ? 1 : 0);
    } else {
      throw new UnsupportedOperationException("" + value);
    }
  }

  private static int sizeOfString(String s) {
    byte[] valueBytes = s.getBytes(StandardCharsets.UTF_8);
    return valueBytes.length;
  }

  private static int sizeOfValue(Object value) {
    Buffer buffer = Buffer.buffer();
    ProtoEncoder encoder = new ProtoEncoder(buffer);
    writeValue(value, encoder);
    return buffer.length();
  }

  private static int sizeOfStruct(Map<String, Object> value) {
    Buffer buffer = Buffer.buffer();
    ProtoEncoder encoder = new ProtoEncoder(buffer);
    writeStruct(value, encoder);
    return buffer.length();
  }

  private static int sizeOfList(List<Object> value) {
    Buffer buffer = Buffer.buffer();
    ProtoEncoder encoder = new ProtoEncoder(buffer);
    writeList(value, encoder);
    return buffer.length();
  }
}
