package io.vertx.tests.common;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.StructParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProtoDecoderTest {

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
}
