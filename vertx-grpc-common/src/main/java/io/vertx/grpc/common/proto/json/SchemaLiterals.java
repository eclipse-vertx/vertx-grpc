package io.vertx.grpc.common.proto.json;

import io.vertx.grpc.common.proto.schema.Field;
import io.vertx.grpc.common.proto.schema.MessageType;
import io.vertx.grpc.common.proto.schema.Schema;
import io.vertx.grpc.common.proto.schema.SchemaCompiler;

class SchemaLiterals {

  private static final Schema SCHEMA;

  static {
    SchemaCompiler compiler = new SchemaCompiler();
    compiler.compile(com.google.protobuf.Struct.getDescriptor());
    SCHEMA = compiler.schema();;
  }

  static class Struct {

    static final MessageType TYPE;
    static final Field fields;

    static {
      TYPE = SchemaLiterals.SCHEMA.of("Struct");
      fields = Struct.TYPE.field(1);
    }
  }

  static class Value {

    static final MessageType TYPE;
    static final Field null_value;
    static final Field number_value;
    static final Field string_value;
    static final Field bool_value;
    static final Field struct_value;
    static final Field list_value;

    static {
      TYPE = SchemaLiterals.SCHEMA.of("Value");
      null_value = TYPE.field(1);
      number_value = TYPE.field(2);
      string_value = TYPE.field(3);
      bool_value = TYPE.field(4);
      struct_value = TYPE.field(5);
      list_value = TYPE.field(6);
    }
  }

  static class ListValue {

    static final MessageType TYPE;
    static final Field values;

    static {
      TYPE = SchemaLiterals.SCHEMA.of("ListValue");
      values = TYPE.field(1);
    }
  }

  static class FieldsEntry {

    static final MessageType TYPE;
    static final Field key;
    static final Field value;

    static {
      TYPE = (MessageType) Struct.TYPE.field(1).type;
      key = TYPE.field(1);
      value = TYPE.field(2);
    }
  }

  static {
/*
    Schema schema = new Schema();
    MessageType field = schema.of("Field");
    MessageType struct = schema.of("Struct");
    struct.addField(1, field);
    MessageType list = schema.of("ListValue");
    MessageType value = schema.of("Value");
    value.addField(1, BuiltInTypes.ENUM);
    value.addField(2, BuiltInTypes.DOUBLE);
    value.addField(3, BuiltInTypes.STRING);
    value.addField(4, BuiltInTypes.BOOL);
    value.addField(5, struct);
    value.addField(6, list);
    list.addField(1, value);
    field.addField(1, BuiltInTypes.STRING);
    field.addField(2, value);
*/
    SchemaCompiler compiler = new SchemaCompiler();
    compiler.compile(com.google.protobuf.Struct.getDescriptor());

//    Value.TYPE = compiler.compile(com.google.protobuf.Value.getDescriptor());
//    ListValue.TYPE = compiler.compile(com.google.protobuf.ListValue.getDescriptor());
//    FieldsEntry.TYPE = (MessageType) Struct.TYPE.field(1).type;
  }

}
