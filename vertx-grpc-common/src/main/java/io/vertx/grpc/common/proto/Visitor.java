package io.vertx.grpc.common.proto;

import io.vertx.grpc.common.proto.schema.Field;
import io.vertx.grpc.common.proto.schema.MessageType;

public interface Visitor {

  void init(MessageType type);

  void visitVarInt32(Field field, int v);

  void visitString(Field field, String s);

  void visitDouble(Field field, double d);

  void enter(Field field);

  void leave(Field field);

  void destroy();

}
