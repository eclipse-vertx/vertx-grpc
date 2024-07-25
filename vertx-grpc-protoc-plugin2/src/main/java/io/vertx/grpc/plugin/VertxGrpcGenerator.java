package io.vertx.grpc.plugin;

import com.salesforce.jprotoc.ProtocPlugin;

public class VertxGrpcGenerator {

  public static void main(String[] args) {
    VertxGrpcGeneratorImpl generator = new VertxGrpcGeneratorImpl(true, true);
    if (args.length == 0) {
      ProtocPlugin.generate(generator);
    } else {
      ProtocPlugin.debug(generator, args[0]);
    }
  }
}
