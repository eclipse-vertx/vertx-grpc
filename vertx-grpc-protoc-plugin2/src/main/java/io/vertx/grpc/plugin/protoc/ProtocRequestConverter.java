package io.vertx.grpc.plugin.protoc;

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import io.vertx.grpc.plugin.descriptors.MethodDescriptor;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.descriptors.TranscodingDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProtocRequestConverter {

  private final ProtobufTypeMapper typeMapper;

  public ProtocRequestConverter(ProtobufTypeMapper typeMapper) {
    this.typeMapper = typeMapper;
  }

  public static ProtocRequestConverter create(PluginProtos.CodeGeneratorRequest request) {
    ProtobufTypeMapper typeMapper = new ProtobufTypeMapper(request.getProtoFileList());
    return new ProtocRequestConverter(typeMapper);
  }

  public List<ServiceDescriptor> convertServices(PluginProtos.CodeGeneratorRequest request) {
    List<ServiceDescriptor> services = new ArrayList<>();

    for (DescriptorProtos.FileDescriptorProto fileProto : request.getProtoFileList()) {
      if (!request.getFileToGenerateList().contains(fileProto.getName())) {
        continue; // Skip files not requested for generation
      }

      for (int serviceIndex = 0; serviceIndex < fileProto.getServiceCount(); serviceIndex++) {
        DescriptorProtos.ServiceDescriptorProto serviceProto = fileProto.getService(serviceIndex);
        ServiceDescriptor service = convertService(serviceProto, fileProto, serviceIndex);
        services.add(service);
      }
    }

    return services;
  }

  private ServiceDescriptor convertService(DescriptorProtos.ServiceDescriptorProto serviceProto, DescriptorProtos.FileDescriptorProto fileProto, int serviceIndex) {
    ServiceDescriptor service = new ServiceDescriptor()
      .setName(serviceProto.getName())
      .setPackageName(fileProto.getPackage())
      .setJavaPackage(typeMapper.getJavaPackage(fileProto))
      .setOuterClass(typeMapper.getJavaOuterClassname(fileProto))
      .setDeprecated(serviceProto.hasOptions() && serviceProto.getOptions().getDeprecated());

    // Extract documentation
    String documentation = extractServiceDocumentation(fileProto, serviceIndex);
    if (!documentation.isEmpty()) {
      service.setDocumentation(documentation);
    }

    // Convert methods
    for (int methodIndex = 0; methodIndex < serviceProto.getMethodCount(); methodIndex++) {
      DescriptorProtos.MethodDescriptorProto methodProto = serviceProto.getMethod(methodIndex);
      MethodDescriptor method = convertMethod(methodProto, fileProto, methodIndex);
      service.addMethod(method);
    }

    return service;
  }

  private MethodDescriptor convertMethod(DescriptorProtos.MethodDescriptorProto methodProto, DescriptorProtos.FileDescriptorProto fileProto, int methodIndex) {
    MethodDescriptor method = new MethodDescriptor()
      .setName(methodProto.getName())
      .setInputType(typeMapper.toJavaTypeName(methodProto.getInputType()))
      .setOutputType(typeMapper.toJavaTypeName(methodProto.getOutputType()))
      .setClientStreaming(methodProto.getClientStreaming())
      .setServerStreaming(methodProto.getServerStreaming())
      .setDeprecated(methodProto.hasOptions() && methodProto.getOptions().getDeprecated())
      .setMethodNumber(methodIndex);

    // Extract documentation
    String documentation = extractMethodDocumentation(fileProto, methodIndex);
    if (!documentation.isEmpty()) {
      method.setDocumentation(documentation);
    }

    // Convert transcoding if present
    if (methodProto.hasOptions() && methodProto.getOptions().hasExtension(AnnotationsProto.http)) {
      HttpRule httpRule = methodProto.getOptions().getExtension(AnnotationsProto.http);
      TranscodingDescriptor transcoding = convertTranscoding(httpRule);
      method.setTranscoding(transcoding);
    }

    return method;
  }

  private TranscodingDescriptor convertTranscoding(HttpRule httpRule) {
    TranscodingDescriptor transcoding = new TranscodingDescriptor()
      .setSelector(httpRule.getSelector())
      .setBody(httpRule.getBody())
      .setResponseBody(httpRule.getResponseBody());

    switch (httpRule.getPatternCase()) {
      case GET:
        transcoding.setMethod("GET").setPath(httpRule.getGet());
        break;
      case POST:
        transcoding.setMethod("POST").setPath(httpRule.getPost());
        break;
      case PUT:
        transcoding.setMethod("PUT").setPath(httpRule.getPut());
        break;
      case DELETE:
        transcoding.setMethod("DELETE").setPath(httpRule.getDelete());
        break;
      case PATCH:
        transcoding.setMethod("PATCH").setPath(httpRule.getPatch());
        break;
      case CUSTOM:
        transcoding.setMethod(httpRule.getCustom().getKind()).setPath(httpRule.getCustom().getPath());
        break;
    }

    for (HttpRule additionalBinding : httpRule.getAdditionalBindingsList()) {
      TranscodingDescriptor additional = convertTranscoding(additionalBinding);
      transcoding.addAdditionalBinding(additional);
    }

    return transcoding;
  }

  private String extractServiceDocumentation(DescriptorProtos.FileDescriptorProto fileProto, int serviceIndex) {
    return extractDocumentation(fileProto, Arrays.asList(
      DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER, serviceIndex));
  }

  private String extractMethodDocumentation(DescriptorProtos.FileDescriptorProto fileProto, int methodIndex) {
    // TODO: Extract method documentation
    return "";
  }

  private String extractDocumentation(DescriptorProtos.FileDescriptorProto fileProto, List<Integer> path) {
    for (DescriptorProtos.SourceCodeInfo.Location location : fileProto.getSourceCodeInfo().getLocationList()) {
      if (location.getPathList().equals(path)) {
        String comments = location.getLeadingComments().isEmpty() ? location.getTrailingComments() : location.getLeadingComments();
        return formatJavaDoc(comments);
      }
    }
    return "";
  }

  private String formatJavaDoc(String comments) {
    if (comments == null || comments.trim().isEmpty()) {
      return "";
    }

    StringBuilder builder = new StringBuilder("/**\n");
    String[] lines = HtmlEscapers.htmlEscaper().escape(comments).split("\n");

    for (String line : lines) {
      String escaped = line.trim()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("*/", "&#42;&#47;")
        .replace("*", "&#42;");
      builder.append("     * ").append(escaped).append("\n");
    }

    builder.append("     */");

    return builder.toString();
  }
}
