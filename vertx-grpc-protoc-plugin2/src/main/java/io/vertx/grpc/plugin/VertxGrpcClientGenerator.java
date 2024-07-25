package io.vertx.grpc.plugin;

import com.salesforce.jprotoc.ProtocPlugin;

public class VertxGrpcClientGenerator {

  public static void main(String[] args) {
    VertxGrpcGeneratorImpl generator = new VertxGrpcGeneratorImpl(true, false);
    if (args.length == 0) {
      ProtocPlugin.generate(generator);
    } else {
      ProtocPlugin.debug(generator, args[0]);
    }
  }
}
