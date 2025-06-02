package io.vertx.tests.transcoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
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

  private void addBinding(String path, String value) {
    List<String> fieldPath = Arrays.asList(path.split("\\."));
    HttpVariableBinding binding = new HttpVariableBinding(fieldPath, value);
    bindings.add(binding);
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
      null
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
      null
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
      null
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
      null
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
      "*"
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
      "nested.data"
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

    assertThrows(IllegalStateException.class, () -> MessageWeaver.weaveResponseMessage(
      Buffer.buffer(message.encode()),
      "invalid.path"
    ));
  }

  @Test
  public void testInvalidJson() {
    assertThrows(DecodeException.class, () -> MessageWeaver.weaveRequestMessage(
      Buffer.buffer("invalid json"),
      new ArrayList<>(),
      "*"
    ));

    assertThrows(DecodeException.class, () -> MessageWeaver.weaveResponseMessage(
      Buffer.buffer("invalid json"),
      "*"
    ));
  }
}
