package io.vertx.grpc.common.proto.schema;

import java.util.HashSet;
import java.util.Set;

public class EnumType implements Type {

  private Set<Integer> values = new HashSet<>();

  @Override
  public TypeID id() {
    return TypeID.ENUM;
  }

  @Override
  public WireType wireType() {
    return WireType.VARINT;
  }
}
