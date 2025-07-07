package io.vertx.grpc.common.proto.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.Visitor;
import io.vertx.grpc.common.proto.schema.Field;
import io.vertx.grpc.common.proto.schema.MessageType;
import io.vertx.grpc.common.proto.ProtobufReader;

import java.util.Stack;

public class JsonReader {

  public static JsonObject parseStruct(Buffer buffer) {
    JsonBuilder builder = new JsonBuilder();
    ProtobufReader.parse(SchemaLiterals.Struct.TYPE, builder, buffer);
    return (JsonObject) builder.stack.pop();
  }

  private static class JsonBuilder implements Visitor {

    private Stack<Object> stack = new Stack<>();
    private Stack<MessageType> current = new Stack<>();

    @Override
    public void init(MessageType type) {
      current.add(type);
      if (type == SchemaLiterals.Struct.TYPE) {
        stack.add(new JsonObject());
      }
    }

    @Override
    public void visitVarInt32(Field field, int v) {
      if (field == SchemaLiterals.Value.null_value) {
        // NULL
        visitValue(null);
      } else if (field == SchemaLiterals.Value.bool_value) {
        if (v == 1) {
          visitValue(true);
        } else if (v == 0) {
          visitValue(false);
        } else {
          throw new DecodeException();
        }
      } else {
        throw new UnsupportedOperationException();
      }
    }

    public void visitValue(Object value) {
      MessageType m = current.peek();
      if (m == SchemaLiterals.Value.TYPE) {
        Object o = stack.peek();
        if (o instanceof String) {
          stack.add(value);
        } else if (o instanceof JsonArray) {
          ((JsonArray)o).add(value);
        } else {
          throw new UnsupportedOperationException();
        }
      } else if (m == SchemaLiterals.FieldsEntry.TYPE) {
        stack.add(value);
      } else {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public void visitString(Field field, String s) {
      visitValue(s);
    }

    @Override
    public void visitDouble(Field field, double d) {
      visitValue(d);
    }

    @Override
    public void enter(Field field) {
      MessageType mt = (MessageType) field.type;
      current.add(mt);
      if (mt == SchemaLiterals.Struct.TYPE) {
        stack.add(new JsonObject());
      } else if (mt == SchemaLiterals.ListValue.TYPE) {
        stack.add(new JsonArray());
      } else if (mt == SchemaLiterals.FieldsEntry.TYPE) {
      } else if (mt == SchemaLiterals.Value.TYPE) {
      } else {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public void leave(Field field) {
      MessageType pop = current.pop();
      if (pop == SchemaLiterals.FieldsEntry.TYPE) {
        Object value = stack.pop();
        String key = (String) stack.pop();
        ((JsonObject)stack.peek()).put(key, value);
      } else if (pop == SchemaLiterals.Struct.TYPE) {
        visitValue(stack.pop());
      } else if (pop == SchemaLiterals.ListValue.TYPE) {
        visitValue(stack.pop());
      }
    }

    @Override
    public void destroy() {
    }
  }
}
