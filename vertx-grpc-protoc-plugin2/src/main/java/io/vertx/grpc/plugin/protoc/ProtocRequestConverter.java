package io.vertx.grpc.plugin.protoc;

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import io.vertx.grpc.plugin.descriptors.MessageDescriptor;
import io.vertx.grpc.plugin.descriptors.MethodDescriptor;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.descriptors.TranscodingDescriptor;

import java.util.*;

/**
 * Responsible for converting Protocol Buffers definitions into internal representations used for further processing or code generation. This class primarily focuses on
 * transforming service definitions, method details, and HTTP transcoding rules while maintaining the associated metadata.
 */
public class ProtocRequestConverter {

  private final ProtobufTypeMapper typeMapper;
  private final Map<String, DescriptorProtos.FileDescriptorProto> filesByName = new HashMap<>();
  private final Map<String, MessageDescriptor> messagesByProtoType = new HashMap<>();
  private final Map<String, DescriptorProtos.DescriptorProto> protoMessagesByType = new HashMap<>();

  public ProtocRequestConverter(ProtobufTypeMapper typeMapper, List<DescriptorProtos.FileDescriptorProto> files) {
    this.typeMapper = typeMapper;
    // Index all files for lookup
    for (DescriptorProtos.FileDescriptorProto file : files) {
      filesByName.put(file.getName(), file);
      // Index all messages in this file
      for (DescriptorProtos.DescriptorProto message : file.getMessageTypeList()) {
        indexMessage(message, file.getPackage(), "");
      }
    }
  }

  private void indexMessage(DescriptorProtos.DescriptorProto message, String protoPackage, String prefix) {
    String protoTypeName = "." + protoPackage + "." + prefix + message.getName();
    protoMessagesByType.put(protoTypeName, message);

    // Index nested messages
    for (DescriptorProtos.DescriptorProto nested : message.getNestedTypeList()) {
      indexMessage(nested, protoPackage, prefix + message.getName() + ".");
    }
  }

  /**
   * Creates an instance of ProtocRequestConverter using the provided CodeGeneratorRequest.
   *
   * @param request the CodeGeneratorRequest containing protocol buffer file definitions and metadata
   * @return a new instance of ProtocRequestConverter initialized with a ProtobufTypeMapper created from the given request
   */
  public static ProtocRequestConverter create(PluginProtos.CodeGeneratorRequest request) {
    ProtobufTypeMapper typeMapper = new ProtobufTypeMapper(request.getProtoFileList());
    return new ProtocRequestConverter(typeMapper, request.getProtoFileList());
  }

  /**
   * Gets all collected message descriptors.
   *
   * @return a map of Java type names to MessageDescriptor objects
   */
  public Map<String, MessageDescriptor> getMessageDescriptors() {
    return new HashMap<>(messagesByProtoType);
  }

  /**
   * Converts a protobuf message type to a MessageDescriptor.
   *
   * @param protoTypeName the protobuf type name (e.g., ".mypackage.MyMessage")
   * @return the MessageDescriptor or null if not found
   */
  public MessageDescriptor convertMessage(String protoTypeName) {
    // Check cache first
    if (messagesByProtoType.containsKey(protoTypeName)) {
      return messagesByProtoType.get(protoTypeName);
    }

    DescriptorProtos.DescriptorProto messageProto = protoMessagesByType.get(protoTypeName);
    if (messageProto == null) {
      return null;
    }

    String javaType = typeMapper.toJavaTypeName(protoTypeName);
    MessageDescriptor message = new MessageDescriptor()
      .setName(messageProto.getName())
      .setFullName(protoTypeName)
      .setJavaType(javaType);

    // Convert fields
    for (DescriptorProtos.FieldDescriptorProto fieldProto : messageProto.getFieldList()) {
      MessageDescriptor.FieldDescriptor field = convertField(fieldProto);
      message.addField(field);
    }

    messagesByProtoType.put(protoTypeName, message);
    return message;
  }

  private MessageDescriptor.FieldDescriptor convertField(DescriptorProtos.FieldDescriptorProto fieldProto) {
    MessageDescriptor.FieldDescriptor field = new MessageDescriptor.FieldDescriptor()
      .setName(fieldProto.getName())
      .setNumber(fieldProto.getNumber())
      .setRepeated(fieldProto.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
      .setRequired(fieldProto.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED);

    // Set json_name
    if (fieldProto.hasJsonName() && !fieldProto.getJsonName().isEmpty()) {
      field.setJsonName(fieldProto.getJsonName());
    } else {
      // Default json_name is camelCase version of field name
      field.setJsonName(toCamelCase(fieldProto.getName()));
    }

    // Determine type
    String protoType = getProtoFieldType(fieldProto);
    field.setType(protoType);

    // Set Java type
    if (fieldProto.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE ||
        fieldProto.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
      field.setJavaType(typeMapper.toJavaTypeName(fieldProto.getTypeName()));
    } else {
      field.setJavaType(mapFieldTypeToJava(fieldProto.getType()));
    }

    return field;
  }

  private String getProtoFieldType(DescriptorProtos.FieldDescriptorProto fieldProto) {
    switch (fieldProto.getType()) {
      case TYPE_DOUBLE: return "double";
      case TYPE_FLOAT: return "float";
      case TYPE_INT64: return "int64";
      case TYPE_UINT64: return "uint64";
      case TYPE_INT32: return "int32";
      case TYPE_FIXED64: return "fixed64";
      case TYPE_FIXED32: return "fixed32";
      case TYPE_BOOL: return "bool";
      case TYPE_STRING: return "string";
      case TYPE_BYTES: return "bytes";
      case TYPE_UINT32: return "uint32";
      case TYPE_SFIXED32: return "sfixed32";
      case TYPE_SFIXED64: return "sfixed64";
      case TYPE_SINT32: return "sint32";
      case TYPE_SINT64: return "sint64";
      case TYPE_MESSAGE: return fieldProto.getTypeName();
      case TYPE_ENUM: return fieldProto.getTypeName();
      default: return "unknown";
    }
  }

  private String mapFieldTypeToJava(DescriptorProtos.FieldDescriptorProto.Type type) {
    switch (type) {
      case TYPE_DOUBLE: return "double";
      case TYPE_FLOAT: return "float";
      case TYPE_INT64:
      case TYPE_UINT64:
      case TYPE_FIXED64:
      case TYPE_SFIXED64:
      case TYPE_SINT64:
        return "long";
      case TYPE_INT32:
      case TYPE_UINT32:
      case TYPE_FIXED32:
      case TYPE_SFIXED32:
      case TYPE_SINT32:
        return "int";
      case TYPE_BOOL: return "boolean";
      case TYPE_STRING: return "String";
      case TYPE_BYTES: return "ByteString";
      default: return "Object";
    }
  }

  private String toCamelCase(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }

    return result.toString();
  }

  /**
   * Converts a Protocol Buffers service descriptor (ServiceDescriptorProto) into a ServiceDescriptor. This method maps the service's attributes and associated methods to an
   * internal representation used for further processing or code generation.
   *
   * @param serviceProto the Protocol Buffers ServiceDescriptorProto object representing the service to convert
   * @param fileProto the Protocol Buffers FileDescriptorProto object containing the file-level metadata for the service
   * @param serviceIndex the index of the service within the file descriptor, used to extract documentation or other metadata
   * @return a ServiceDescriptor object representing the converted service with its metadata and methods mapped
   */
  public ServiceDescriptor convertService(DescriptorProtos.ServiceDescriptorProto serviceProto, DescriptorProtos.FileDescriptorProto fileProto, int serviceIndex) {
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

  /**
   * Converts a Protocol Buffers method definition into an internal representation of a method descriptor.
   *
   * @param methodProto the Protocol Buffers method descriptor to be converted
   * @param fileProto the Protocol Buffers file descriptor containing the method
   * @param methodIndex the index of the method in the service
   * @return the converted method descriptor containing detailed information about the method
   */
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

  /**
   * Converts an {@code HttpRule} into a {@code TranscodingDescriptor} by mapping its components such as method type, path, selector, body, response body, and additional bindings.
   *
   * @param httpRule the {@code HttpRule} instance to be converted into a {@code TranscodingDescriptor}
   * @return a {@code TranscodingDescriptor} that represents the provided {@code HttpRule} with its associated properties
   */
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

  /**
   * Extracts the documentation associated with a specific service in the given protocol buffer file descriptor.
   *
   * @param fileProto the FileDescriptorProto containing information about protocol buffer definitions.
   * @param serviceIndex the index of the service in the file descriptor whose documentation is to be extracted.
   * @return a String representation of the service's documentation, formatted as JavaDoc, or an empty string if no documentation is found.
   */
  private String extractServiceDocumentation(DescriptorProtos.FileDescriptorProto fileProto, int serviceIndex) {
    return extractDocumentation(fileProto, Arrays.asList(
      DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER, serviceIndex));
  }

  /**
   * Extracts the method documentation for a specific method within the provided FileDescriptorProto.
   *
   * @param fileProto the FileDescriptorProto containing the file's metadata and descriptors.
   * @param methodIndex the index of the method within the file's method descriptors for which the documentation is to be extracted.
   * @return the extracted documentation for the specified method as a String, or an empty string if no documentation is found.
   */
  private String extractMethodDocumentation(DescriptorProtos.FileDescriptorProto fileProto, int methodIndex) {
    // TODO: Extract method documentation
    return "";
  }

  /**
   * Extracts documentation comments from a FileDescriptorProto based on the provided path. The method searches for a `SourceCodeInfo.Location` in the FileDescriptorProto, which
   * matches the given path and extracts its leading or trailing comments. The extracted comments are then formatted as JavaDoc.
   *
   * @param fileProto The FileDescriptorProto containing the source code information with comments.
   * @param path The path within the FileDescriptorProto to locate the desired comments.
   * @return A formatted JavaDoc string representation of the comments, or an empty string if no comments are found for the specified path.
   */
  private String extractDocumentation(DescriptorProtos.FileDescriptorProto fileProto, List<Integer> path) {
    for (DescriptorProtos.SourceCodeInfo.Location location : fileProto.getSourceCodeInfo().getLocationList()) {
      if (location.getPathList().equals(path)) {
        String comments = location.getLeadingComments().isEmpty() ? location.getTrailingComments() : location.getLeadingComments();
        return formatJavaDoc(comments);
      }
    }
    return "";
  }

  /**
   * Formats the given comments string into a Javadoc-style comment block. The method escapes special characters and ensures that the resulting comment is properly structured to
   * comply with Javadoc formatting standards.
   *
   * @param comments the input comments string to be formatted into a Javadoc-style block; can be null or empty.
   * @return a formatted Javadoc-style comment string, or an empty string if the input comments are null or empty.
   */
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
