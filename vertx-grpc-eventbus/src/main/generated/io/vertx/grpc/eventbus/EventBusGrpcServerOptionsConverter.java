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
      }
    }
  }

   static void toJson(EventBusGrpcServerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(EventBusGrpcServerOptions obj, java.util.Map<String, Object> json) {
  }
}
