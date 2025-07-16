package io.vertx.grpc.common.proto.schema;

public enum WireType {

  VARINT(0),
  I64(1),
  LEN(2),
  I32(5);

  public final int id;

  WireType(int id) {
    this.id = id;
  }
}
