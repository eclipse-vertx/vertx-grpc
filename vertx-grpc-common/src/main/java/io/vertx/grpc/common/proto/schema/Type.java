package io.vertx.grpc.common.proto.schema;

public interface Type {
  TypeID id();
  WireType wireType();
}
