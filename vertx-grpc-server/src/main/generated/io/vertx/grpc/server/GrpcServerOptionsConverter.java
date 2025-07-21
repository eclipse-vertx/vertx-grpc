package io.vertx.grpc.server;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

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
    json.put("scheduleDeadlineAutomatically", obj.getScheduleDeadlineAutomatically());
    json.put("deadlinePropagation", obj.getDeadlinePropagation());
    json.put("maxMessageSize", obj.getMaxMessageSize());
  }
}
