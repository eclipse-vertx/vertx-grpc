package io.vertx.grpc.plugin;

import com.google.api.AnnotationsProto;
import com.salesforce.jprotoc.ProtocPlugin;

import java.util.*;

public class VertxGrpcGenerator {

  public static void main(String[] args) {
    boolean generateClient = true;
    boolean generateService = true;
    boolean generateIo = false;
    if (args.length > 0) {
      generateClient = false;
      generateService = false;
      for (String arg : args) {
        switch (arg) {
          case "grpc-client":
            generateClient = true;
            break;
          case "grpc-service":
            generateService = true;
            break;
          case "grpc-io":
            generateIo = true;
            break;
        }
      }
    }
    ProtocPlugin.generate(List.of(new VertxGrpcGeneratorImpl(generateClient, generateService, generateIo)), List.of(AnnotationsProto.http));
  }
}
