package io.vertx.grpc.plugin;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
  name = "vertx-grpc-generator",
  mixinStandardHelpOptions = true,
  version = "vertx-grpc-generator 1.0",
  description = "Generates Vert.x gRPC code from proto files."
)
public class VertxGrpcGeneratorCommand implements Callable<Integer> {

  @CommandLine.Option(names = { "--grpc-client" }, description = "Generate gRPC client code")
  public boolean grpcClient = false;

  @CommandLine.Option(names = { "--grpc-service" }, description = "Generate gRPC service code")
  public boolean grpcService = false;

  @CommandLine.Option(names = { "--grpc-io" }, description = "Generate gRPC IO code")
  public boolean grpcIo = false;

  @CommandLine.Option(names = { "--grpc-transcoding" }, description = "Whether to generate transcoding options for methods with HTTP annotations")
  public boolean grpcTranscoding = true;

  @CommandLine.Option(names = { "--vertx-codegen" }, description = "Whether to generate vertx generator annotations")
  public boolean vertxCodegen = false;

  @CommandLine.Option(
    names = { "--service-prefix" },
    description = "Generate service classes with a prefix. For example, if you set it to `MyService`, the generated service class will be `MyServiceGreeterService` instead of `GreeterService`."
  )
  public String servicePrefix = "";

  @Override
  public Integer call() {
    if (!grpcClient && !grpcService && !grpcIo) {
      grpcClient = true;
      grpcService = true;
    }

    return 0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("grpc-client=").append(grpcClient).append(", ");
    sb.append("grpc-service=").append(grpcService).append(", ");
    sb.append("grpc-io=").append(grpcIo).append(", ");
    sb.append("grpc-transcoding=").append(grpcTranscoding).append(", ");
    sb.append("vertx-codegen=").append(vertxCodegen).append(", ");

    if (servicePrefix != null && !servicePrefix.isBlank()) {
      sb.append("service-prefix=").append(servicePrefix);
    }

    return sb.toString();
  }
}
