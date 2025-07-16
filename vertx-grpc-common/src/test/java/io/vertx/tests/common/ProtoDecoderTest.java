package io.vertx.tests.common;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.poc.StructParser;
import io.vertx.grpc.common.proto.poc.StructWriter;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class ProtoDecoderTest {

  private long foo(byte[] bytes) {
    long value = 0;
    int idx = 0;
    for (int i = 0; i < 64; i += 7) {
      byte b = bytes[idx++];
      value |= (long) (b & 0x7F) << i;
      if (b >= 0) {
        return value;
      }
    }
    throw new AssertionError();
  }

  @Test
  public void testFormatAbuse() throws Exception {
    byte[] bytes = Value.newBuilder().setStringValue("foo").build().toByteArray();
    // 1a03666f6f
    // 1a 80 03 666f6f
    //

    assertEquals(3, foo(new byte[] { 0x03 }));
    assertEquals(3, foo(new byte[] { (byte)0b10000011, 0x0 }));

    bytes = new byte[] {
      0x1A,
      (byte)0x83,
      (byte)0x80,
      0x00,
      0x66,
      0x6f,
      0x6f
    };

    Value value = Value.parseFrom(bytes);
    assertEquals("foo", value.getStringValue());

  }

  @Test
  public void testDecodeJson() throws Exception {
    testJson(new JsonObject());
    testJson(new JsonObject().put("string-1", "the-string-1").put("string-2", "the-string-2"));
    testJson(new JsonObject().put("number-1", 1234).put("number-2", 4321));
    testJson(new JsonObject().put("object", new JsonObject().put("string", "the-string")));
    testJson(new JsonObject().put("object", new JsonArray().add(1)));
    testJson(new JsonObject().put("null-1", null).put("null-2", null));
    testJson(new JsonObject().put("true", true).put("false", false));
  }

  private void testJson(Object value) throws Exception {
    String s = Json.encode(value);
    Struct.Builder builder = Struct.newBuilder();
    JsonFormat.parser().merge(s, builder);
    byte[] protobuf = builder.build().toByteArray();
    Buffer buffer = Buffer.buffer(protobuf);
    JsonObject json = StructParser.parseStruct(buffer);
    assertEquals(value, json);
  }

  @Test
  public void testEncodeJson() throws Exception {
    testEncodeJson(new JsonObject().put("string-1", "the-string-1").put("string-2", "the-string-2"));
    testEncodeJson(new JsonObject().put("number-1", 1234).put("number-2", 4321));
    testEncodeJson(new JsonObject().put("object", new JsonObject().put("string", "the-string")));
    testEncodeJson(new JsonObject().put("object", new JsonArray().add(1)));
    testEncodeJson(new JsonObject().put("null-1", null).put("null-2", null));
    testEncodeJson(new JsonObject().put("true", true).put("false", false));
  }

  private void testEncodeJson(JsonObject json) throws Exception {
    Buffer serialized = StructWriter.toProto(json);
    Struct.Builder builder = Struct.newBuilder();
    JsonFormat.parser().merge(json.encode(), builder);
    byte[] real = builder.build().toByteArray();
    String S1 = new BigInteger(1, serialized.getBytes()).toString(16);
    String S2 = new BigInteger(1, real).toString(16);
    assertEquals(S1, S2);
  }

}
