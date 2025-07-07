package io.vertx.grpc.common.proto.schema;

public class Field {

  private final MessageType owner;
  public final int number;
  public final Type type;

  public Field(MessageType owner, int number, Type type) {
    this.owner = owner;
    this.number = number;
    this.type = type;
  }

  public String toString() {
    return "Field[number=" + number + ",type=" + type + ",owner=" + owner.name() + "]";
  }
}
