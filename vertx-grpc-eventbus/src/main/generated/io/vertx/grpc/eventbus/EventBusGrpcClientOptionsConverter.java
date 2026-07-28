package io.vertx.grpc.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.grpc.eventbus.EventBusGrpcClientOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.grpc.eventbus.EventBusGrpcClientOptions} original class using Vert.x codegen.
 */
public class EventBusGrpcClientOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, EventBusGrpcClientOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "pingInterval":
          if (member.getValue() instanceof Number) {
            obj.setPingInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "pingTimeout":
          if (member.getValue() instanceof Number) {
            obj.setPingTimeout(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(EventBusGrpcClientOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(EventBusGrpcClientOptions obj, java.util.Map<String, Object> json) {
    json.put("pingInterval", obj.getPingInterval());
    json.put("pingTimeout", obj.getPingTimeout());
  }
}
