package io.vertx.grpc.plugin;

import com.google.api.AnnotationsProto;
import com.salesforce.jprotoc.ProtocPlugin;

import java.util.List;

public class VertxGrpcServerGenerator {
  public static void main(String[] args) {
    if (args.length == 0) {
      ProtocPlugin.generate(List.of(new VertxGrpcGeneratorImpl(false, true, true)), List.of(AnnotationsProto.http));
    } else {
      ProtocPlugin.debug(List.of(new VertxGrpcGeneratorImpl(false, true, true)), List.of(AnnotationsProto.http), args[0]);
    }
  }
}
