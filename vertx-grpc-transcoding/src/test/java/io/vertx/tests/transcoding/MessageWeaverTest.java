package io.vertx.tests.transcoding;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;
import io.vertx.grpc.transcoding.impl.MessageWeaver;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class MessageWeaverTest {

  private List<HttpVariableBinding> bindings;

  @Before
  public void setUp() {
    bindings = new ArrayList<>();
  }

  private static Descriptors.Descriptor buildTestDescriptor() {
    try {
      DescriptorProtos.FieldDescriptorProto yField = DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("y").setNumber(1).setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL).build();
      DescriptorProtos.FieldDescriptorProto xRepeated = DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("x").setNumber(2).setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED).build();
      DescriptorProtos.FieldDescriptorProto keysRepeated = DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("keys").setNumber(4).setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED).build();

      DescriptorProtos.DescriptorProto innerMsg = DescriptorProtos.DescriptorProto.newBuilder()
        .setName("InnerA").addField(yField).addField(xRepeated).build();

      DescriptorProtos.FieldDescriptorProto nestedField = DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("A").setNumber(3).setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName("InnerA")
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL).build();

      DescriptorProtos.DescriptorProto rootMsg = DescriptorProtos.DescriptorProto.newBuilder()
        .setName("Root").addField(yField).addField(xRepeated).addField(nestedField).addField(keysRepeated).addNestedType(innerMsg).build();

      DescriptorProtos.FileDescriptorProto fileProto = DescriptorProtos.FileDescriptorProto.newBuilder()
        .setName("test.proto").addMessageType(rootMsg).build();

      Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileProto, new Descriptors.FileDescriptor[]{});
      return fileDescriptor.findMessageTypeByName("Root");
    } catch (Descriptors.DescriptorValidationException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Descriptors.Descriptor TEST_DESCRIPTOR = buildTestDescriptor();

  private void addBinding(String path, String value) {
    List<String> fieldNames = Arrays.asList(path.split("\\."));
    bindings.add(new HttpVariableBinding(fieldNames, value));
  }

  @Test
  public void testPassThroughRequest() {
    JsonObject message = new JsonObject()
      .put("A", new JsonObject()
        .put("x", "a")
        .put("by", "b")
        .put("i", 1)
        .put("ui", 2)
        .put("i64", 3)
        .put("ui64", 4)
        .put("b", true)
        .putNull("null")
        .put("B", new JsonObject()
          .put("y", "b")));

    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(message.encode()),
      new ArrayList<>(),
      "*",
      TEST_DESCRIPTOR
    );

    assertEquals(message, new JsonObject(result.toString()));
  }

  @Test
  public void testLevel0Bindings() {
    addBinding("_x", "a");
    addBinding("_y", "b");
    addBinding("_z", "c");

    JsonObject original = new JsonObject()
      .put("i", 10)
      .put("x", "d");

    JsonObject expected = new JsonObject()
      .put("i", 10)
      .put("x", "d")
      .put("_x", "a")
      .put("_y", "b")
      .put("_z", "c");

    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(original.encode()),
      bindings,
      "*",
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testLevel1Bindings() {
    addBinding("A._x", "a");
    addBinding("A._y", "b");
    addBinding("B._x", "c");

    JsonObject original = new JsonObject()
      .put("x", "d")
      .put("A", new JsonObject().put("y", "e"))
      .put("B", new JsonObject().put("z", "f"));

    JsonObject expected = new JsonObject()
      .put("x", "d")
      .put("A", new JsonObject()
        .put("y", "e")
        .put("_x", "a")
        .put("_y", "b"))
      .put("B", new JsonObject()
        .put("z", "f")
        .put("_x", "c"));

    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(original.encode()),
      bindings,
      "*",
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testLevel2Bindings() {
    addBinding("A.B._x", "a");
    addBinding("A.C._y", "b");
    addBinding("D.E._x", "c");

    JsonObject original = new JsonObject()
      .put("A", new JsonObject()
        .put("B", new JsonObject().put("x", "d"))
        .put("y", "e")
        .put("C", new JsonObject()))
      .put("D", new JsonObject()
        .put("z", "f")
        .put("E", new JsonObject().put("u", "g")));

    JsonObject expected = new JsonObject()
      .put("A", new JsonObject()
        .put("B", new JsonObject()
          .put("x", "d")
          .put("_x", "a"))
        .put("y", "e")
        .put("C", new JsonObject()
          .put("_y", "b")))
      .put("D", new JsonObject()
        .put("z", "f")
        .put("E", new JsonObject()
          .put("u", "g")
          .put("_x", "c")));

    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(original.encode()),
      bindings,
      "*",
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testTranscodingRequestBodyWildcard() {
    JsonObject message = new JsonObject()
      .put("field1", "value1")
      .put("field2", "value2");

    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(message.encode()),
      new ArrayList<>(),
      "*",
      TEST_DESCRIPTOR
    );

    assertEquals(message, new JsonObject(result.toString()));
  }

  @Test
  public void testTranscodingRequestBodyPath() {
    JsonObject message = new JsonObject()
      .put("field1", "value1")
      .put("field2", "value2");

    JsonObject expected = new JsonObject().put("nested", new JsonObject().put("data", message));

    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(message.encode()),
      new ArrayList<>(),
      "nested.data",
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testResponsePassThrough() {
    JsonObject message = new JsonObject()
      .put("field1", "value1")
      .put("field2", "value2");

    Buffer result = MessageWeaver.weaveResponseMessage(
      Buffer.buffer(message.encode()),
      null
    );

    assertEquals(message, new JsonObject(result.toString()));
  }

  @Test
  public void testResponseBodyWildcard() {
    JsonObject message = new JsonObject()
      .put("field1", "value1")
      .put("field2", "value2");

    Buffer result = MessageWeaver.weaveResponseMessage(
      Buffer.buffer(message.encode()),
      "*"
    );

    assertEquals(message, new JsonObject(result.toString()));
  }

  @Test
  public void testResponseBodyPath() {
    JsonObject nested = new JsonObject()
      .put("field1", "value1")
      .put("field2", "value2");

    JsonObject message = new JsonObject()
      .put("response", new JsonObject()
        .put("data", nested));

    Buffer result = MessageWeaver.weaveResponseMessage(
      Buffer.buffer(message.encode()),
      "response.data"
    );

    assertEquals(nested, new JsonObject(result.toString()));
  }

  @Test
  public void testResponseBodyInvalidPath() {
    JsonObject message = new JsonObject()
      .put("field1", "value1");

    assertThrows(IllegalArgumentException.class, () -> MessageWeaver.weaveResponseMessage(
      Buffer.buffer(message.encode()),
      "invalid.path"
    ));
  }

  @Test
  public void testRepeatedBindingsLevel0() {
    addBinding("x", "a");
    addBinding("x", "b");
    addBinding("x", "c");

    JsonObject expected = new JsonObject()
      .put("x", new JsonArray().add("a").add("b").add("c"));

    Buffer result = MessageWeaver.weaveRequestMessage(
      null,
      bindings,
      null,
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testRepeatedBindingsLevel1() {
    addBinding("A.x", "b");
    addBinding("A.x", "c");
    addBinding("A.x", "d");

    JsonObject expected = new JsonObject()
      .put("A", new JsonObject()
        .put("x", new JsonArray().add("b").add("c").add("d")));

    Buffer result = MessageWeaver.weaveRequestMessage(
      null,
      bindings,
      null,
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testRepeatedBindingsWithBody() {
    addBinding("A.x", "b");
    addBinding("A.x", "c");
    addBinding("A.x", "d");

    JsonObject body = new JsonObject()
      .put("A", new JsonObject().put("y", "e"));

    JsonObject expected = new JsonObject()
      .put("A", new JsonObject()
        .put("x", new JsonArray().add("b").add("c").add("d"))
        .put("y", "e"));

    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(body.encode()),
      bindings,
      "*",
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testRepeatedBindingsMixedWithSingle() {
    addBinding("x", "a");
    addBinding("x", "b");
    addBinding("y", "c");

    JsonObject expected = new JsonObject()
      .put("x", new JsonArray().add("a").add("b"))
      .put("y", "c");

    Buffer result = MessageWeaver.weaveRequestMessage(
      null,
      bindings,
      null,
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testRepeatedBindingsTwoValues() {
    addBinding("keys", "A");
    addBinding("keys", "B");

    JsonObject expected = new JsonObject()
      .put("keys", new JsonArray().add("A").add("B"));

    Buffer result = MessageWeaver.weaveRequestMessage(
      null,
      bindings,
      null,
      TEST_DESCRIPTOR
    );

    assertEquals(expected, new JsonObject(result.toString()));
  }

  @Test
  public void testEmptyMessageReturnsEmptyObject() {
    Buffer fromNull = MessageWeaver.weaveRequestMessage(null, new ArrayList<>(), null, TEST_DESCRIPTOR);
    assertEquals(new JsonObject(), new JsonObject(fromNull.toString()));

    Buffer fromEmpty = MessageWeaver.weaveRequestMessage(Buffer.buffer(), new ArrayList<>(), null, TEST_DESCRIPTOR);
    assertEquals(new JsonObject(), new JsonObject(fromEmpty.toString()));

    Buffer fromEmptyBody = MessageWeaver.weaveRequestMessage(Buffer.buffer(), new ArrayList<>(), "", TEST_DESCRIPTOR);
    assertEquals(new JsonObject(), new JsonObject(fromEmptyBody.toString()));
  }

  @Test
  public void testUnsetBodyIgnoresHttpBody() {
    JsonObject httpBody = new JsonObject().put("field1", "value1");
    Buffer result = MessageWeaver.weaveRequestMessage(
      Buffer.buffer(httpBody.encode()),
      new ArrayList<>(),
      null,
      TEST_DESCRIPTOR
    );
    assertEquals(new JsonObject(), new JsonObject(result.toString()));
  }

  @Test
  public void testInvalidJson() {
    assertThrows(DecodeException.class, () -> MessageWeaver.weaveRequestMessage(
      Buffer.buffer("invalid json"),
      new ArrayList<>(),
      "invalid",
      TEST_DESCRIPTOR
    ));

    assertThrows(DecodeException.class, () -> MessageWeaver.weaveResponseMessage(
      Buffer.buffer("invalid json"),
      "invalid"
    ));
  }
}
