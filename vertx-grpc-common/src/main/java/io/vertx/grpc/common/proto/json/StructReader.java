package io.vertx.grpc.common.proto.json;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.proto.ProtobufReader;
import io.vertx.grpc.common.proto.Visitor;
import io.vertx.grpc.common.proto.schema.Field;
import io.vertx.grpc.common.proto.schema.MessageType;

import java.util.Stack;

public class StructReader {

  public static Struct parse(Buffer buffer) {
    VisitorImpl visitor = new VisitorImpl();
    ProtobufReader.parse(SchemaLiterals.Struct.TYPE, visitor, buffer);
    return null;
  }

  private static class VisitorImpl implements Visitor {

    private Stack<Object> stack = new Stack<>();

    @Override
    public void init(MessageType type) {
      if (type == SchemaLiterals.Struct.TYPE) {
        stack.add(Struct.newBuilder());
      }
    }

    @Override
    public void visitVarInt32(Field field, int v) {
      if (field == SchemaLiterals.Value.null_value) {
        stack.push(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
      } else if (field == SchemaLiterals.Value.bool_value) {
        stack.push(Value.newBuilder().setBoolValue(v == 1).build());
      } else {
        throw new UnsupportedOperationException("" + field);
      }
    }

    @Override
    public void visitString(Field field, String s) {
      if (field == SchemaLiterals.FieldsEntry.key) {
        stack.add(s);
      } else if (field == SchemaLiterals.Value.string_value) {
        stack.add(Value.newBuilder().setStringValue(s).build());
      } else {
        throw new UnsupportedOperationException("" + field);
      }
    }

    @Override
    public void visitDouble(Field field, double d) {
      if (field == SchemaLiterals.Value.number_value) {
        stack.add(Value.newBuilder().setNumberValue(d).build());
      } else {
        throw new UnsupportedOperationException("" + field);
      }
    }

    @Override
    public void enter(Field field) {
      if (field == SchemaLiterals.ListValue.values) {
        //
      } else if (field == SchemaLiterals.Struct.fields) {
        //
      } else if (field == SchemaLiterals.FieldsEntry.value) {
        //
      } else if (field == SchemaLiterals.Value.struct_value) {
        stack.push(Struct.newBuilder());
      } else if (field == SchemaLiterals.Value.list_value) {
        stack.push(ListValue.newBuilder());
      } else {
        throw new UnsupportedOperationException("" + field);
      }
    }

    @Override
    public void leave(Field field) {
      if (field == SchemaLiterals.FieldsEntry.value) {
        Value value = (Value) stack.pop();
        String key = (String) stack.pop();
        Struct.Builder builder = (Struct.Builder) stack.peek();
        builder.putFields(key, value);
      } else if (field == SchemaLiterals.Struct.fields) {
        //
      } else if (field == SchemaLiterals.Value.struct_value) {
        Struct.Builder struct = (Struct.Builder) stack.pop();
        stack.push(Value.newBuilder().setStructValue(struct).build());
      } else if (field == SchemaLiterals.ListValue.values) {
        Value value = (Value) stack.pop();
        ListValue.Builder builder = (ListValue.Builder) stack.peek();
        builder.addValues(value);
      } else if (field == SchemaLiterals.Value.list_value) {
        ListValue.Builder list = (ListValue.Builder) stack.pop();
        stack.push(Value.newBuilder().setListValue(list).build());
      } else {
        throw new UnsupportedOperationException("" + field);
      }
    }

    @Override
    public void destroy() {
    }
  }
}
