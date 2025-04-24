package io.vertx.grpc.plugin;

import com.google.api.AnnotationsProto;
import com.salesforce.jprotoc.ProtocPlugin;

import java.util.*;

public class VertxGrpcGenerator {

  public enum TranscodingMode {
    DISABLED,
    OPTION_ONLY,
    ALL
  }

  public static void main(String[] args) {
    boolean generateClient = true;
    boolean generateService = true;
    boolean generateIo = false;
    TranscodingMode transcodingMode = TranscodingMode.ALL; // Default is ALL

    if (args.length > 0) {
      generateClient = false;
      generateService = false;
      for (String arg : args) {
        if (arg.startsWith("--transcoding=")) {
          String mode = arg.substring("--transcoding=".length());
          switch (mode.toLowerCase()) {
            case "disabled":
              transcodingMode = TranscodingMode.DISABLED;
              break;
            case "option-only":
              transcodingMode = TranscodingMode.OPTION_ONLY;
              break;
            case "all":
              transcodingMode = TranscodingMode.ALL;
              break;
            default:
              System.err.println("Invalid transcoding mode: " + mode + ". Using default: all");
              break;
          }
        } else {
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
    }
    ProtocPlugin.generate(List.of(new VertxGrpcGeneratorImpl(generateClient, generateService, generateIo, transcodingMode)), List.of(AnnotationsProto.http));
  }
}
