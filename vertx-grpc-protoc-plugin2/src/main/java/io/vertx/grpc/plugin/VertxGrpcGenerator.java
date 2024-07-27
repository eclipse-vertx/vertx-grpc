package io.vertx.grpc.plugin;

import com.salesforce.jprotoc.ProtocPlugin;

public class VertxGrpcGenerator {
  public static void main(String[] args) {
    if (args.length == 0) {
      ProtocPlugin.generate(new VertxGrpcGeneratorImpl(true, true));
    } else {
      ProtocPlugin.debug(new VertxGrpcGeneratorImpl(true, true), args[0]);
    }
  }
}
