package io.vertx.grpc.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.grpc.eventbus.EventBusGrpcServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.grpc.eventbus.EventBusGrpcServerOptions} original class using Vert.x codegen.
 */
public class EventBusGrpcServerOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, EventBusGrpcServerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "maxConcurrentStreams":
          if (member.getValue() instanceof Number) {
            obj.setMaxConcurrentStreams(((Number)member.getValue()).intValue());
          }
          break;
        case "supportedWireFormats":
          if (member.getValue() instanceof JsonArray) {
            java.util.LinkedHashSet<io.vertx.grpc.common.WireFormat> list =  new java.util.LinkedHashSet<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add(io.vertx.grpc.common.WireFormat.valueOf((String)item));
            });
            obj.setSupportedWireFormats(list);
          }
          break;
      }
    }
  }

   static void toJson(EventBusGrpcServerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(EventBusGrpcServerOptions obj, java.util.Map<String, Object> json) {
    json.put("maxConcurrentStreams", obj.getMaxConcurrentStreams());
    if (obj.getSupportedWireFormats() != null) {
      JsonArray array = new JsonArray();
      obj.getSupportedWireFormats().forEach(item -> array.add(item.name()));
      json.put("supportedWireFormats", array);
    }
  }
}
