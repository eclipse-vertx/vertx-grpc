package io.vertx.grpc.plugin.generation;

import io.vertx.grpc.plugin.descriptors.MessageDescriptor;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenerationContext {

  private final String packageName;
  private final String outputDirectory;
  private final GenerationOptions options;
  private final List<ServiceDescriptor> services;
  private final Map<String, Object> templateVariables = new HashMap<>();
  private final Map<String, MessageDescriptor> messageDescriptors;

  public GenerationContext(String packageName, String outputDirectory, List<ServiceDescriptor> services, GenerationOptions options) {
    this(packageName, outputDirectory, services, options, Collections.emptyMap());
  }

  public GenerationContext(String packageName, String outputDirectory, List<ServiceDescriptor> services, GenerationOptions options, Map<String, MessageDescriptor> messageDescriptors) {
    this.packageName = packageName;
    this.outputDirectory = outputDirectory;
    this.services = services;
    this.options = options;
    this.messageDescriptors = messageDescriptors;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getOutputDirectory() {
    return outputDirectory;
  }

  public GenerationOptions getOptions() {
    return options;
  }

  public List<ServiceDescriptor> getServices() {
    return services;
  }

  public Map<String, Object> getTemplateVariables() {
    return templateVariables;
  }

  public Map<String, MessageDescriptor> getMessageDescriptors() {
    return messageDescriptors;
  }
}
