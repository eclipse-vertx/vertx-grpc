package io.vertx.grpc.common.proto.json;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.Visitor;

import java.util.Map;

class JsonDriver {

  static void visitStruct(JsonObject json, Visitor visitor) {
    visitor.init(SchemaLiterals.Struct.TYPE);
    visitStructInternal(json, visitor);
    visitor.destroy();
  }

  private static void visitStructInternal(JsonObject json, Visitor visitor) {
    for (Map.Entry<String, Object> entry : json) {
      visitor.enter(SchemaLiterals.Struct.fields); // fields
      visitor.visitString(SchemaLiterals.FieldsEntry.key, entry.getKey());
      visitor.enter(SchemaLiterals.FieldsEntry.value);
      visitValueInternal(entry.getValue(), visitor);
      visitor.leave(SchemaLiterals.FieldsEntry.value);
      visitor.leave(SchemaLiterals.Struct.fields);
    }
  }

  private static void visitListInternal(JsonArray json, Visitor visitor) {
    for (Object value : json) {
      visitor.enter(SchemaLiterals.ListValue.values); // values
      visitValueInternal(value, visitor);
      visitor.leave(SchemaLiterals.ListValue.values);
    }
  }

  private static void visitValueInternal(Object value, Visitor visitor) {
    if (value == null) {
      visitor.visitVarInt32(SchemaLiterals.Value.null_value, 0);
    } else if (value instanceof String) {
      visitor.visitString(SchemaLiterals.Value.string_value, (String) value);
    } else if (value instanceof Boolean) {
      visitor.visitVarInt32(SchemaLiterals.Value.bool_value, ((Boolean) value) ? 1 : 0);
    } else if (value instanceof Number) {
      visitor.visitDouble(SchemaLiterals.Value.number_value, ((Number) value).doubleValue());
    } else if (value instanceof JsonObject) {
      visitor.enter(SchemaLiterals.Value.struct_value);
      visitStructInternal((JsonObject) value, visitor);
      visitor.leave(SchemaLiterals.Value.struct_value);
    } else if (value instanceof JsonArray) {
      visitor.enter(SchemaLiterals.Value.list_value);
      visitListInternal((JsonArray) value, visitor);
      visitor.leave(SchemaLiterals.Value.list_value);
    } else {
      throw new UnsupportedOperationException("" + value);
    }
  }
}
