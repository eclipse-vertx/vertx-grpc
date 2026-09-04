package io.vertx.grpc.plugin.generation.context;

import io.vertx.grpc.plugin.generation.GenerationOptions;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ServiceTemplateContext {

  public String javaPackageFqn;
  public String serviceName;
  public String packageName;
  public String prefixedServiceName;
  public String contractFqn;
  public String serviceFqn;
  public String clientFqn;
  public String outerFqn;
  public boolean codegenEnabled;
  public String javaDoc;

  public List<MethodTemplateContext> allMethods;
  public List<MethodTemplateContext> methods;
  public List<MethodTemplateContext> serviceMethods;
  public List<MethodTemplateContext> transcodingMethods;
  public List<MethodTemplateContext> unaryUnaryMethods;
  public List<MethodTemplateContext> unaryManyMethods;
  public List<MethodTemplateContext> manyUnaryMethods;
  public List<MethodTemplateContext> manyManyMethods;

  public static ServiceTemplateContext fromServiceDescriptor(ServiceDescriptor service, GenerationOptions options) {
    ServiceTemplateContext context = new ServiceTemplateContext();

    // Basic enableService info
    context.javaPackageFqn = service.getJavaPackage();
    context.serviceName = service.getName();
    context.packageName = service.getPackageName();
    context.codegenEnabled = options.isGenerateVertxGeneratorAnnotations();
    context.javaDoc = service.getDocumentation();

    // Build the names with prefix, the templates declare their own type from prefixedServiceName and refer to the other
    // generated types with the package qualified names
    String prefix = options.getServicePrefix();
    String packageQualifier = context.javaPackageFqn == null || context.javaPackageFqn.isEmpty() ? "" : context.javaPackageFqn + ".";
    context.prefixedServiceName = prefix + service.getName();
    context.contractFqn = packageQualifier + context.prefixedServiceName;
    context.clientFqn = packageQualifier + context.prefixedServiceName + "Client";
    context.serviceFqn = packageQualifier + context.prefixedServiceName + "Service";
    context.outerFqn = packageQualifier + service.getOuterClass();

    // Convert methods
    context.allMethods = service.getMethods().stream()
      .map(MethodTemplateContext::fromMethodDescriptor)
      .collect(Collectors.toList());

    context.methods = new ArrayList<>(context.allMethods);

    // Filter methods by type
    context.unaryUnaryMethods = service.getUnaryUnaryMethods().stream()
      .map(MethodTemplateContext::fromMethodDescriptor)
      .collect(Collectors.toList());

    context.unaryManyMethods = service.getUnaryStreamMethods().stream()
      .map(MethodTemplateContext::fromMethodDescriptor)
      .collect(Collectors.toList());

    context.manyUnaryMethods = service.getStreamUnaryMethods().stream()
      .map(MethodTemplateContext::fromMethodDescriptor)
      .collect(Collectors.toList());

    context.manyManyMethods = service.getStreamStreamMethods().stream()
      .map(MethodTemplateContext::fromMethodDescriptor)
      .collect(Collectors.toList());

    // Filter enableService methods (non-transcoding)
    context.serviceMethods = service.getMethods().stream()
      .filter(m -> m.getTranscoding() == null)
      .map(MethodTemplateContext::fromMethodDescriptor)
      .collect(Collectors.toList());

    // Filter transcoding methods
    context.transcodingMethods = service.getTranscodingMethods().stream()
      .map(MethodTemplateContext::fromMethodDescriptor)
      .collect(Collectors.toList());

    return context;
  }
}
