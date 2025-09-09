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
  public String contractFqn;
  public String serviceFqn;
  public String clientFqn;
  public String grpcClientFqn;
  public String grpcServiceFqn;
  public String grpcIoFqn;
  public String outerFqn;
  public String prefixedServiceName;
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
    context.outerFqn = service.getOuterClass();
    context.codegenEnabled = options.isGenerateVertxGeneratorAnnotations();
    context.javaDoc = service.getDocumentation();

    // Build FQN names with prefix
    String prefix = options.getServicePrefix();
    context.contractFqn = prefix + service.getName();
    context.clientFqn = prefix + service.getName() + "Client";
    context.serviceFqn = prefix + service.getName() + "Service";
    context.grpcClientFqn = prefix + service.getName() + "GrpcClient";
    context.grpcServiceFqn = prefix + service.getName() + "GrpcService";
    context.grpcIoFqn = prefix + service.getName() + "GrpcIo";
    context.prefixedServiceName = prefix + service.getName();

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
