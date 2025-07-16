package io.vertx.grpc.common.proto.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.proto.Visitor;
import io.vertx.grpc.common.proto.ProtobufWriter;

import java.util.function.Consumer;

public class JsonWriter {

  public static Buffer encode(JsonObject json) {
    Consumer<Visitor> consumer = visitor -> {
      JsonDriver.visitStruct(json, visitor);
    };
    return ProtobufWriter.encode(consumer);
  }

}
