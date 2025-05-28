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
        case "enabledProtocols":
          if (member.getValue() instanceof JsonArray) {
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                obj.addEnabledProtocol(io.vertx.grpc.server.GrpcProtocol.valueOf((String)item));
            });
          }
          break;
        case "compressionEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setCompressionEnabled((Boolean)member.getValue());
          }
          break;
        case "compressionAlgorithms":
          if (member.getValue() instanceof JsonArray) {
            java.util.LinkedHashSet<java.lang.String> list =  new java.util.LinkedHashSet<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setCompressionAlgorithms(list);
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
    if (obj.getEnabledProtocols() != null) {
      JsonArray array = new JsonArray();
      obj.getEnabledProtocols().forEach(item -> array.add(item.name()));
      json.put("enabledProtocols", array);
    }
    json.put("compressionEnabled", obj.isCompressionEnabled());
    if (obj.getCompressionAlgorithms() != null) {
      JsonArray array = new JsonArray();
      obj.getCompressionAlgorithms().forEach(item -> array.add(item));
      json.put("compressionAlgorithms", array);
    }
    json.put("scheduleDeadlineAutomatically", obj.getScheduleDeadlineAutomatically());
    json.put("deadlinePropagation", obj.getDeadlinePropagation());
    json.put("maxMessageSize", obj.getMaxMessageSize());
  }
}
