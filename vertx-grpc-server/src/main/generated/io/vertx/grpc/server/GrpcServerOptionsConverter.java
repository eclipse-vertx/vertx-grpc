package io.vertx.grpc.server;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.grpc.server.GrpcServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.grpc.server.GrpcServerOptions} original class using Vert.x codegen.
 */
public class GrpcServerOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, GrpcServerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "grpcWebEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setGrpcWebEnabled((Boolean)member.getValue());
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
  }
}
