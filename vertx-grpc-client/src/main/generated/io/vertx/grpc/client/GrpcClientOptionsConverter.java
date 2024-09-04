package io.vertx.grpc.client;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.grpc.client.GrpcClientOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.grpc.client.GrpcClientOptions} original class using Vert.x codegen.
 */
public class GrpcClientOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, GrpcClientOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "maxMessageSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxMessageSize(((Number)member.getValue()).longValue());
          }
          break;
        case "transportOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setTransportOptions(new io.vertx.core.http.HttpClientOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
      }
    }
  }

   static void toJson(GrpcClientOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(GrpcClientOptions obj, java.util.Map<String, Object> json) {
    json.put("maxMessageSize", obj.getMaxMessageSize());
    if (obj.getTransportOptions() != null) {
      json.put("transportOptions", obj.getTransportOptions().toJson());
    }
  }
}
