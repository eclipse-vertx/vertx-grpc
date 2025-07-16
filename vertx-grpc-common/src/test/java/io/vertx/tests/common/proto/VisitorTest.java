package io.vertx.tests.common.proto;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.json.JsonReader;
import io.vertx.grpc.common.proto.json.JsonWriter;
import io.vertx.grpc.common.proto.json.StructReader;
import io.vertx.grpc.common.proto.json.StructWriter;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class VisitorTest {


  @Test
  public void testDecode() throws Exception {

    testDecodeJson(new JsonObject().put("string-1", "the-string-1").put("string-2", "the-string-2"));
    testDecodeJson(new JsonObject().put("number-1", 0).put("number-2", 4321));
    testDecodeJson(new JsonObject().put("object", new JsonObject().put("string", "the-string")));
    testDecodeJson(new JsonObject().put("object", new JsonArray().add(1)));
    testDecodeJson(new JsonObject().put("null-1", null).put("null-2", null));
    testDecodeJson(new JsonObject().put("true", true).put("false", false));
    testDecodeJson(new JsonObject().put("object", new JsonArray().add(new JsonObject().put("string", "the-string")).add(4)));

  }

  private void testDecodeJson(Object value) throws Exception {
    String s = Json.encode(value);
    Struct.Builder builder = Struct.newBuilder();
    JsonFormat.parser().merge(s, builder);
    byte[] protobuf = builder.build().toByteArray();
    Buffer buffer = Buffer.buffer(protobuf);
    JsonObject json = JsonReader.parseStruct(buffer);
    assertEquals(value, json);

    StructReader.parse(Buffer.buffer(protobuf));

  }

  @Test
  public void testEncode() throws Exception {

    // (1,2) 26 (fields)
    //    (1,2) 8 (string)
    //       737472696e672d31
    //    (2,2) 14 (value)
    //       (3,2) 12 (string)
    //          7468652d737472696e672d31
    //
    //    **    **
    // 0a 07 0a 01 66 12 02 08 00       0a 08 0a02666612020800
    // 0a 08 0a 01 66 12 02 08 00       0a 07 0a02666612020800

    // (19, 8, 2, 7, 2)
    // (19, 7, 2, 8, 2)

    testEncodeJson(new JsonObject().put("string-1", "the-string-1").put("string-2", "the-string-2"));
    testEncodeJson(new JsonObject().put("number-1", 0).put("number-2", 4321));
    testEncodeJson(new JsonObject().put("object", new JsonObject().put("string", "the-string")));
    testEncodeJson(new JsonObject().put("object", new JsonArray().add(1)));
    testEncodeJson(new JsonObject().put("null-1", null).put("null-2", null));
    testEncodeJson(new JsonObject().put("true", true).put("false", false));
    testEncodeJson(new JsonObject().put("object", new JsonArray().add(new JsonObject().put("string", "the-string")).add(4)));

    //    **    **               **
    // 0a 0c 0a 05 66616c7365 12 02 08 00       0a 0b 0a066e756c6c2d3212020800
    // 0a 0b 0a 05 66616c7365 12 02 08 00       0a 0c 0a066e756c6c2d3212020800

    // 2
    // 4

    // 0a 15 0a 08 6e756d6265722d31 12 09 11 0000000000000000
    // 0a 15 0a 08 6e756d6265722d31 12 09 19 0000000000000000


  }

  private void testEncodeJson(JsonObject json) throws Exception {
    Buffer buffer = JsonWriter.encode(json);
    String S1 = new BigInteger(1, buffer.getBytes()).toString(16);

    Struct.Builder builder = Struct.newBuilder();
    JsonFormat.parser().merge(json.encode(), builder);
    byte[] real = builder.build().toByteArray();
    String S2 = new BigInteger(1, real).toString(16);

    assertEquals(S2, S1);

    //
    String S3 = new BigInteger(1, StructWriter.encode(builder.build()).getBytes()).toString(16);
    assertEquals(S2, S3);
  }
}
