package io.vertx.grpc.plugin.generation.context;

import io.vertx.grpc.plugin.descriptors.MethodDescriptor;

public class MethodTemplateContext {

  public String methodName;
  public String vertxMethodName;
  public String inputType;
  public String outputType;
  public boolean deprecated;
  public boolean isManyInput;
  public boolean isManyOutput;
  public String vertxCallsMethodName;
  public String grpcCallsMethodName;
  public int methodNumber;
  public String javaDoc;
  public String methodHeader;
  public TranscodingTemplateContext transcodingContext;

  public static MethodTemplateContext fromMethodDescriptor(MethodDescriptor method) {
    MethodTemplateContext context = new MethodTemplateContext();

    context.methodName = method.getName();
    context.vertxMethodName = method.getVertxMethodName();
    context.inputType = method.getInputType();
    context.outputType = method.getOutputType();
    context.deprecated = method.isDeprecated();
    context.isManyInput = method.isClientStreaming();
    context.isManyOutput = method.isServerStreaming();
    context.vertxCallsMethodName = method.getVertxCallsMethodName();
    context.grpcCallsMethodName = method.getGrpcCallsMethodName();
    context.methodNumber = method.getMethodNumber();
    context.javaDoc = method.getDocumentation();

    // Build method header
    context.methodHeader = buildMethodHeader(method);

    // Convert transcoding if present
    if (method.getTranscoding() != null) {
      context.transcodingContext = TranscodingTemplateContext.fromTranscodingDescriptor(method.getTranscoding());
    }

    return context;
  }

  public String methodNameUpperUnderscore() {
    return NameUtils.toUpperUnderscore(methodName);
  }

  public String methodNameGetter() {
    return NameUtils.mixedLower("get_" + methodName + "_method");
  }

  private static String buildMethodHeader(MethodDescriptor method) {
    StringBuilder header = new StringBuilder();

    if (method.getDocumentation() != null && !method.getDocumentation().trim().isEmpty()) {
      header.append(method.getDocumentation());
    }

    if (method.isDeprecated()) {
      if (header.length() > 0) {
        header.append("\n");
      }
      header.append("        @Deprecated");
    }

    return header.toString();
  }
}
