package io.vertx.grpc.plugin;

import com.salesforce.jprotoc.ProtocPlugin;

public class VertxGrpcServerGenerator {
  public static void main(String[] args) {
    if (args.length == 0) {
      ProtocPlugin.generate(new VertxGrpcGeneratorImpl(false, true));
    } else {
      ProtocPlugin.debug(new VertxGrpcGeneratorImpl(false, true), args[0]);
    }
  }
}
