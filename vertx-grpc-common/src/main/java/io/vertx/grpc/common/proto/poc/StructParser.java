package io.vertx.grpc.common.proto.poc;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.ProtoDecoder;

/**
 * A parser for struct.proto schema that decodes to Vert.x JSon.
 */
public class StructParser {

  public static Object parseValue(Buffer buffer) {
    ProtoDecoder decoder = new ProtoDecoder(buffer);
    return parseValue(decoder);
  }

  public static JsonObject parseStruct(Buffer buffer) {
    ProtoDecoder decoder = new ProtoDecoder(buffer);
    return parseStruct(decoder);
  }

  public static JsonArray parseListValue(Buffer buffer) {
    ProtoDecoder decoder = new ProtoDecoder(buffer);
    return parseListValue(decoder);
  }

  private static JsonObject parseStruct(ProtoDecoder decoder) {
    JsonObject object = new JsonObject();
    // fields
    while (decoder.isReadable()) {
      assertTrue(decoder.readTag());
      assertEquals(1, decoder.fieldNumber());
      assertEquals(2, decoder.wireType());
      assertTrue(decoder.readVarInt()); // LEN
      int end = decoder.index() + decoder.int32Value();
      int backup = decoder.len();
      decoder.len(end);
      String key = null;
      Object value = null;
      try {
        while (decoder.isReadable()) {
          assertTrue(decoder.readTag());
          switch (decoder.fieldNumber()) {
            case 1:
              // Read key
              assertEquals(1, decoder.fieldNumber());
              assertEquals(2, decoder.wireType());
              assertTrue(decoder.readVarInt());
              int keyLength = decoder.int32Value(); // LEN
              key = decoder.readString(keyLength);
              break;
            case 2:
              // Read value
              assertEquals(2, decoder.fieldNumber());
              assertEquals(2, decoder.wireType());
              assertTrue(decoder.readVarInt());
              int valueLength = decoder.int32Value(); // LEN
              value = parseValue(decoder);
              break;
            default:
              throw new AssertionError();
          }
        }
        object.put(key, value);
      } finally {
        decoder.len(backup);
      }
      assertEquals(end, decoder.index());



    }
    return object;
  }

  private static JsonArray parseListValue(ProtoDecoder decoder) {
    JsonArray value = new JsonArray();
    while (decoder.isReadable()) {
      assertTrue(decoder.readTag());
      assertEquals(1, decoder.fieldNumber());
      assertEquals(2, decoder.wireType()); // LEN
      assertTrue(decoder.readVarInt());
      Object o = parseValue(decoder);
      value.add(o);
    }
    return value;
  }

  private static Object parseValue(ProtoDecoder decoder) {
    assertTrue(decoder.readTag());
    int field = decoder.fieldNumber();
    int len, max;
    switch (field) {
      case 1:
        assertEquals(0, decoder.wireType());
        assertTrue(decoder.readVarInt());
        decoder.skip(decoder.int32Value());
        return null;
      case 2:
        assertEquals(1, decoder.wireType());
        assertTrue(decoder.readDouble());
        return decoder.doubleValue();
      case 3:
        assertEquals(2, decoder.wireType());
        assertTrue(decoder.readVarInt());
        len = decoder.int32Value();
        return decoder.readString(len);
      case 4:
        assertEquals(0, decoder.wireType());
        assertTrue(decoder.readVarInt());
        int b = decoder.int32Value();
        switch (b) {
          case 0:
            return false;
          case 1:
            return true;
          default:
            throw new DecodeException();
        }
      case 5:
        assertEquals(2, decoder.wireType());
        assertTrue(decoder.readVarInt());
        len = decoder.int32Value();
        max = decoder.len();
        try {
          decoder.len(decoder.index() + len);
          return parseStruct(decoder);
        } finally {
          decoder.len(max);
        }
      case 6:
        assertEquals(2, decoder.wireType());
        assertTrue(decoder.readVarInt());
        len = decoder.int32Value();
        max = decoder.len();
        try {
          decoder.len(decoder.index() + len);
          return parseListValue(decoder);
        }  finally {
          decoder.len(max);
        }
      default:
        throw new UnsupportedOperationException();
    }
  }

  private static void assertTrue(boolean b) {
    if (!b) {
      throw new DecodeException();
    }
  }

  private static void assertEquals(int expected, int value) {
    if (expected != value) {
      throw new DecodeException();
    }
  }
}
