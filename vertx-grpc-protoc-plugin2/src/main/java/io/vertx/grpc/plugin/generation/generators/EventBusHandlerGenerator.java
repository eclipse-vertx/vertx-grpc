package io.vertx.grpc.plugin.generation.generators;

import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.generation.*;
import io.vertx.grpc.plugin.generation.context.ServiceTemplateContext;

import java.util.ArrayList;
import java.util.List;

public class EventBusHandlerGenerator extends CodeGenerator {

  private static final String TEMPLATE_NAME = "event-bus-handler.mustache";

  public EventBusHandlerGenerator() {
    super(GenerationType.EVENT_BUS_HANDLER);
  }

  @Override
  public GenerationResult generate(GenerationContext context) {
    List<GeneratedFile> files = new ArrayList<>();

    for (ServiceDescriptor service : context.getServices()) {
      try {
        ServiceTemplateContext templateContext = ServiceTemplateContext.fromServiceDescriptor(service, context.getOptions());

        String fileName = context.getOptions().getServicePrefix() + service.getName() + "EventBusHandler.java";
        String relativePath = getRelativePath(context.getPackageName());

        GeneratedFile file = createFile(fileName, relativePath, TEMPLATE_NAME, templateContext);
        files.add(file);
      } catch (Exception e) {
        return GenerationResult.failure(List.of(
          new GenerationError("Failed to generate event bus handler for service: " + service.getName(), e, generationType)
        ));
      }
    }

    return GenerationResult.success(files);
  }
}
