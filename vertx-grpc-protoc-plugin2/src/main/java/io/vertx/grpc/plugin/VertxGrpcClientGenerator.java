package io.vertx.grpc.plugin;

import com.salesforce.jprotoc.ProtocPlugin;

public class VertxGrpcClientGenerator {
  public static void main(String[] args) {
    if (args.length == 0) {
      ProtocPlugin.generate(new VertxGrpcGeneratorImpl(true, false, true));
    } else {
      ProtocPlugin.debug(new VertxGrpcGeneratorImpl(true, false, true), args[0]);
    }
  }
}
