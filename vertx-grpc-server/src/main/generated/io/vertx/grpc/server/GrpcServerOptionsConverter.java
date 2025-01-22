package io.vertx.grpc.server;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.grpc.server.GrpcServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.grpc.server.GrpcServerOptions} original class using Vert.x codegen.
 */
public class GrpcServerOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, GrpcServerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "grpcWebEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setGrpcWebEnabled((Boolean)member.getValue());
          }
          break;
        case "grpcTranscodingEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setGrpcTranscodingEnabled((Boolean)member.getValue());
          }
          break;
        case "scheduleDeadlineAutomatically":
          if (member.getValue() instanceof Boolean) {
            obj.setScheduleDeadlineAutomatically((Boolean)member.getValue());
          }
          break;
        case "deadlinePropagation":
          if (member.getValue() instanceof Boolean) {
            obj.setDeadlinePropagation((Boolean)member.getValue());
          }
          break;
        case "maxMessageSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxMessageSize(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(GrpcServerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(GrpcServerOptions obj, java.util.Map<String, Object> json) {
    json.put("grpcWebEnabled", obj.isGrpcWebEnabled());
    json.put("grpcTranscodingEnabled", obj.isGrpcTranscodingEnabled());
    json.put("scheduleDeadlineAutomatically", obj.getScheduleDeadlineAutomatically());
    json.put("deadlinePropagation", obj.getDeadlinePropagation());
    json.put("maxMessageSize", obj.getMaxMessageSize());
  }
}
