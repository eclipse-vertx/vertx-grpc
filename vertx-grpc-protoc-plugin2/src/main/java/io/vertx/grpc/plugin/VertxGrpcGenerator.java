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

  public enum TranscodingMode {
    DISABLED,
    OPTION,
    ALL
  }

  @Option(names = { "--grpc-client" }, description = "Generate gRPC client code")
  private boolean generateClient = false;

  @Option(names = { "--grpc-service" }, description = "Generate gRPC service code")
  private boolean generateService = false;

  @Option(names = { "--grpc-io" }, description = "Generate gRPC IO code")
  private boolean generateIo = false;

  @Option(names = { "--grpc-transcoding" }, description = "Transcoding mode: disabled, option, all (default: all)", converter = CaseInsensitiveEnumConverter.class)
  private TranscodingMode transcodingMode = TranscodingMode.ALL;

  @Override
  public Integer call() {
    if (!generateClient && !generateService && !generateIo) {
      generateClient = true;
      generateService = true;
    }

    VertxGrpcGeneratorImpl generator = new VertxGrpcGeneratorImpl(generateClient, generateService, generateIo, transcodingMode);
    ProtocPlugin.generate(List.of(generator), List.of(AnnotationsProto.http));

    return 0;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new VertxGrpcGenerator()).execute(args);
    System.exit(exitCode);
  }

  public static class CaseInsensitiveEnumConverter implements CommandLine.ITypeConverter<TranscodingMode> {
    @Override
    public TranscodingMode convert(String value) {
      for (TranscodingMode mode : TranscodingMode.values()) {
        if (mode.name().equalsIgnoreCase(value)) {
          return mode;
        }
      }
      throw new CommandLine.TypeConversionException("Invalid transcoding mode: " + value);
    }
  }
}
