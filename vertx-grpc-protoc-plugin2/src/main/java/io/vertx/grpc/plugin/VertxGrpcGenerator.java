package io.vertx.grpc.plugin;

import com.google.api.AnnotationsProto;
import com.google.common.io.ByteStreams;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.compiler.PluginProtos;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.generation.*;
import io.vertx.grpc.plugin.generation.generators.*;
import io.vertx.grpc.plugin.protoc.ProtocFileWriter;
import io.vertx.grpc.plugin.protoc.ProtocRequestConverter;
import io.vertx.grpc.plugin.template.MustacheTemplateEngine;
import io.vertx.grpc.plugin.template.TemplateEngine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VertxGrpcGenerator {

  private static final List<GeneratedMessage.GeneratedExtension<?, ?>> extensions = List.of(AnnotationsProto.http);
  private static final List<PluginProtos.CodeGeneratorResponse.Feature> supportedFeatures = List.of(
    PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL,
    PluginProtos.CodeGeneratorResponse.Feature.FEATURE_SUPPORTS_EDITIONS
  );

  public static void main(String[] args) {
    try {
      ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
      for (GeneratedMessage.GeneratedExtension<?, ?> extension : extensions) {
        extensionRegistry.add(extension);
      }

      // Read protoc request from stdin
      PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.parseFrom(ByteStreams.toByteArray(System.in), extensionRegistry);

      // Process the request
      PluginProtos.CodeGeneratorResponse response = processRequest(request);

      // Write response to stdout
      response.writeTo(System.out);
    } catch (Exception e) {
      // Write error response
      PluginProtos.CodeGeneratorResponse errorResponse = PluginProtos.CodeGeneratorResponse.newBuilder()
        .setError("Plugin execution failed: " + e.getMessage())
        .build();

      try {
        errorResponse.writeTo(System.out);
      } catch (IOException ioException) {
        System.err.println("Failed to write error response: " + ioException.getMessage());
        System.exit(1);
      }
    }
  }

  private static PluginProtos.CodeGeneratorResponse processRequest(PluginProtos.CodeGeneratorRequest request) {
    try {
      GenerationOptions options = parseOptions(request.getParameter());

      ProtocRequestConverter converter = ProtocRequestConverter.create(request);
      List<ServiceDescriptor> services = converter.convertServices(request);

      List<PluginProtos.CodeGeneratorResponse.File> generatedFiles = generateCode(services, options);

      int featureMask = supportedFeatures.stream()
        .map(PluginProtos.CodeGeneratorResponse.Feature::getNumber)
        .reduce((l, r) -> l | r)
        .orElse(PluginProtos.CodeGeneratorResponse.Feature.FEATURE_NONE_VALUE);

      return PluginProtos.CodeGeneratorResponse.newBuilder()
        .addAllFile(generatedFiles)
        .setSupportedFeatures(featureMask)
        .build();
    } catch (Exception e) {
      return PluginProtos.CodeGeneratorResponse.newBuilder()
        .setError("Code generation failed: " + e.getMessage())
        .build();
    }
  }

  private static GenerationOptions parseOptions(String parameter) {
    GenerationOptions options = new GenerationOptions();

    if (parameter == null || parameter.trim().isEmpty()) {
      // Default: generate generate client and generate service
      return options.setGenerateClient(true).setGenerateService(true);
    }

    // Parse comma-separated options
    Map<String, String> params = parseParameters(parameter);

    // Apply options
    options.setGenerateClient(getBooleanParam(params, "grpc-client", false))
      .setGenerateService(getBooleanParam(params, "grpc-service", false))
      .setGenerateIo(getBooleanParam(params, "grpc-io", false))
      .setGenerateTranscoding(getBooleanParam(params, "grpc-transcoding", true))
      .setGenerateVertxGeneratorAnnotations(getBooleanParam(params, "vertx-codegen", false))
      .setServicePrefix(params.getOrDefault("service-prefix", ""));

    // If nothing specified, default to generate client and generate service
    if (!options.isGenerateClient() && !options.isGenerateService() && !options.isGenerateIo()) {
      options.setGenerateClient(true).setGenerateService(true);
    }

    return options;
  }

  private static Map<String, String> parseParameters(String parameter) {
    Map<String, String> params = new HashMap<>();

    for (String param : parameter.split(",")) {
      String[] parts = param.trim().split("=", 2);
      if (parts.length == 2) {
        params.put(parts[0].trim(), parts[1].trim());
      } else if (parts.length == 1) {
        params.put(parts[0].trim(), "true");
      }
    }

    return params;
  }

  private static boolean getBooleanParam(Map<String, String> params, String key, boolean defaultValue) {
    String value = params.get(key);
    if (value == null) {
      return defaultValue;
    }
    return "true".equalsIgnoreCase(value) || "1".equals(value);
  }

  private static List<PluginProtos.CodeGeneratorResponse.File> generateCode(List<ServiceDescriptor> services, GenerationOptions options) {
    List<PluginProtos.CodeGeneratorResponse.File> allFiles = new ArrayList<>();

    // Group services by package for generation
    Map<String, List<ServiceDescriptor>> servicesByPackage = services.stream().collect(Collectors.groupingBy(ServiceDescriptor::getJavaPackage));

    for (Map.Entry<String, List<ServiceDescriptor>> entry : servicesByPackage.entrySet()) {
      String packageName = entry.getKey();
      List<ServiceDescriptor> packageServices = entry.getValue();

      TemplateEngine templateEngine = new MustacheTemplateEngine();
      GenerationContext context = new GenerationContext(packageName, "", packageServices, options);
      CodeGenerationEngine.Builder builder = CodeGenerationEngine.builder();

      builder.fileWriter(new ProtocFileWriter());
      builder.templateEngine(new MustacheTemplateEngine());

      builder.registerGenerator(GenerationType.CONTRACT, new GrpcContractGenerator(templateEngine))
        .registerGenerator(GenerationType.CLIENT, new GrpcClientGenerator(templateEngine))
        .registerGenerator(GenerationType.GRPC_CLIENT, new GrpcGrpcClientGenerator(templateEngine))
        .registerGenerator(GenerationType.SERVICE, new GrpcServiceGenerator(templateEngine))
        .registerGenerator(GenerationType.GRPC_SERVICE, new GrpcGrpcServiceGenerator(templateEngine))
        .registerGenerator(GenerationType.GRPC_IO, new GrpcIoGenerator(templateEngine));

      GenerationResult result = builder.build().generate(context);

      if (result.isSuccess()) {
        for (GeneratedFile file : result.getFiles()) {
          PluginProtos.CodeGeneratorResponse.File protocFile =
            PluginProtos.CodeGeneratorResponse.File.newBuilder()
              .setName(file.getAbsolutePath())
              .setContent(file.getContent())
              .build();
          allFiles.add(protocFile);
        }
      } else {
        throw new RuntimeException("Generation failed: " +
          result.getErrors().stream()
            .map(GenerationError::getMessage)
            .collect(Collectors.joining(", ")));
      }
    }

    return allFiles;
  }
}
