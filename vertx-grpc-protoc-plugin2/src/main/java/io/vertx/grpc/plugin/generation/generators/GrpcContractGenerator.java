package io.vertx.grpc.plugin.generation.generators;

import io.vertx.grpc.plugin.generation.*;
import io.vertx.grpc.plugin.generation.context.ServiceTemplateContext;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.template.TemplateEngine;

import java.util.ArrayList;
import java.util.List;

public class GrpcContractGenerator extends CodeGenerator {

  private static final String TEMPLATE_NAME = "contract.mustache";

  public GrpcContractGenerator(TemplateEngine templateEngine) {
    super(templateEngine, GenerationType.CONTRACT);
  }

  @Override
  public GenerationResult generate(GenerationContext context) {
    List<GeneratedFile> files = new ArrayList<>();

    for (ServiceDescriptor service : context.getServices()) {
      try {
        ServiceTemplateContext templateContext = ServiceTemplateContext.fromServiceDescriptor(service, context.getOptions());

        String fileName = context.getOptions().getServicePrefix() + service.getName() + ".java";
        String relativePath = getRelativePath(context.getPackageName());

        GeneratedFile file = createFile(fileName, relativePath, TEMPLATE_NAME, templateContext);
        files.add(file);
      } catch (Exception e) {
        return GenerationResult.failure(List.of(
          new GenerationError("Failed to generate contract for enableService: " + service.getName(), e, generationType)
        ));
      }
    }

    return GenerationResult.success(files);
  }
}
