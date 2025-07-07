package io.vertx.grpc.common.proto.schema;

import java.util.HashMap;
import java.util.Map;

public class MessageType implements Type {

  private final String name;
  private final Map<Integer, Field> fields = new HashMap<>();

  public MessageType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public TypeID id() {
    return TypeID.MESSAGE;
  }

  @Override
  public WireType wireType() {
    return WireType.LEN;
  }

  public Field addField(int number, Type type) {
    Field field = new Field(this, number, type);
    fields.put(number, field);
    return field;
  }

  public Field field(int number) {
    return fields.get(number);
  }

  @Override
  public String toString() {
    return "MessageType[name=" + name + "]";
  }
}
