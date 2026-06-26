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
        case "wireFormat":
          if (member.getValue() instanceof String) {
            obj.setWireFormat(io.vertx.grpc.common.WireFormat.valueOf((String)member.getValue()));
          }
          break;
      }
    }
  }

   static void toJson(EventBusGrpcClientOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(EventBusGrpcClientOptions obj, java.util.Map<String, Object> json) {
    if (obj.getWireFormat() != null) {
      json.put("wireFormat", obj.getWireFormat().name());
    }
  }
}
