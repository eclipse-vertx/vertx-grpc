package io.vertx.grpc.plugin.generation.generators;

import io.vertx.grpc.plugin.generation.*;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.generation.context.ServiceTemplateContext;
import io.vertx.grpc.plugin.template.TemplateEngine;

import java.util.ArrayList;
import java.util.List;

public class GrpcGrpcClientGenerator extends CodeGenerator {

  private static final String TEMPLATE_NAME = "grpc-client.mustache";

  public GrpcGrpcClientGenerator(TemplateEngine templateEngine) {
    super(templateEngine, GenerationType.GRPC_CLIENT);
  }

  @Override
  public GenerationResult generate(GenerationContext context) {
    List<GeneratedFile> files = new ArrayList<>();

    for (ServiceDescriptor service : context.getServices()) {
      try {
        ServiceTemplateContext templateContext = ServiceTemplateContext.fromServiceDescriptor(service, context.getOptions());

        String fileName = context.getOptions().getServicePrefix() + service.getName() + "GrpcClient.java";
        String relativePath = getRelativePath(context.getPackageName());

        GeneratedFile file = createFile(fileName, relativePath, TEMPLATE_NAME, templateContext);
        files.add(file);
      } catch (Exception e) {
        return GenerationResult.failure(List.of(
          new GenerationError("Failed to generate gRPC enableClient for enableService: " + service.getName(), e, generationType)
        ));
      }
    }

    return GenerationResult.success(files);
  }
}
