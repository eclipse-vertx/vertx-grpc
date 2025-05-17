package io.vertx.grpc.plugin;

import com.google.api.AnnotationsProto;
import com.salesforce.jprotoc.ProtocPlugin;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.*;
import java.util.concurrent.Callable;

@Command(
  name = "vertx-grpc-generator",
  mixinStandardHelpOptions = true,
  version = "vertx-grpc-generator 1.0",
  description = "Generates Vert.x gRPC code from proto files."
)
public class VertxGrpcGenerator implements Callable<Integer> {

  @Option(names = { "--grpc-client" }, description = "Generate gRPC client code")
  boolean generateClient = false;

  @Option(names = { "--grpc-service" }, description = "Generate gRPC service code")
  boolean generateService = false;

  @Option(names = { "--grpc-io" }, description = "Generate gRPC IO code")
  boolean generateIo = false;

  @Option(names = { "--grpc-transcoding" }, description = "Whether to generate transcoding options for methods with HTTP annotations")
  boolean generateTranscoding = true;

  @Option(names = { "--vertx-codegen" }, description = "Whether to generate vertx generator annotations")
  boolean generateVertxGeneratorAnnotations = false;

  @Option(
    names = { "--service-prefix" },
    description = "Generate service classes with a prefix. For example, if you set it to `MyService`, the generated service class will be `MyServiceGreeterService` instead of `GreeterService`."
  )
  String servicePrefix = "";

  @Override
  public Integer call() {
    if (!generateClient && !generateService && !generateIo) {
      generateClient = true;
      generateService = true;
    }

    VertxGrpcGeneratorImpl generator = new VertxGrpcGeneratorImpl(this);
    ProtocPlugin.generate(List.of(generator), List.of(AnnotationsProto.http));

    return 0;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new VertxGrpcGenerator()).execute(args);
    System.exit(exitCode);
  }
}
