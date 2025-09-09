package io.vertx.grpc.plugin.protoc;

import com.google.protobuf.DescriptorProtos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtobufTypeMapper {

  private final Map<String, DescriptorProtos.FileDescriptorProto> filesByName = new HashMap<>();
  private final Map<String, String> typeToJavaMapping = new HashMap<>();

  public ProtobufTypeMapper(List<DescriptorProtos.FileDescriptorProto> files) {
    // Index all files
    for (DescriptorProtos.FileDescriptorProto file : files) {
      filesByName.put(file.getName(), file);
    }

    // Build type mappings
    buildTypeMappings(files);
  }

  /**
   * Convert a protobuf type name to Java type name
   *
   * @param protoTypeName The protobuf type name (e.g., ".mypackage.MyMessage")
   * @return The Java type name (e.g., "com.example.MyMessage")
   */
  public String toJavaTypeName(String protoTypeName) {
    // Handle primitive types
    if (!protoTypeName.startsWith(".")) {
      return mapPrimitiveType(protoTypeName);
    }

    // Look up in our mapping
    String javaType = typeToJavaMapping.get(protoTypeName);
    if (javaType != null) {
      return javaType;
    }

    // Fallback: try to construct from proto type name
    return constructJavaTypeName(protoTypeName);
  }

  /**
   * Get Java outer class name for a proto file
   */
  public String getJavaOuterClassname(DescriptorProtos.FileDescriptorProto file) {
    DescriptorProtos.FileOptions options = file.getOptions();

    // Check if java_outer_classname is explicitly set
    if (options.hasJavaOuterClassname() && !options.getJavaOuterClassname().isEmpty()) {
      return options.getJavaOuterClassname();
    }

    // Generate from filename
    String filename = file.getName();
    String basename = filename.substring(filename.lastIndexOf('/') + 1);
    if (basename.endsWith(".proto")) {
      basename = basename.substring(0, basename.length() - 6);
    }

    return toCamelCase(basename);
  }

  /**
   * Get Java package name for a proto file
   */
  public String getJavaPackage(DescriptorProtos.FileDescriptorProto file) {
    DescriptorProtos.FileOptions options = file.getOptions();

    // Check if java_package is explicitly set
    if (options.hasJavaPackage() && !options.getJavaPackage().isEmpty()) {
      return options.getJavaPackage();
    }

    // Fallback to proto package
    return file.getPackage();
  }

  private void buildTypeMappings(List<DescriptorProtos.FileDescriptorProto> files) {
    for (DescriptorProtos.FileDescriptorProto file : files) {
      String javaPackage = getJavaPackage(file);
      String outerClass = getJavaOuterClassname(file);
      boolean multipleFiles = file.getOptions().hasJavaMultipleFiles() && file.getOptions().getJavaMultipleFiles();

      // Map messages
      for (DescriptorProtos.DescriptorProto message : file.getMessageTypeList()) {
        mapMessage(message, file.getPackage(), javaPackage, outerClass, multipleFiles, "");
      }

      // Map enums
      for (DescriptorProtos.EnumDescriptorProto enumType : file.getEnumTypeList()) {
        mapEnum(enumType, file.getPackage(), javaPackage, outerClass, multipleFiles, "");
      }
    }
  }

  private void mapMessage(DescriptorProtos.DescriptorProto message, String protoPackage,
    String javaPackage, String outerClass, boolean multipleFiles, String prefix) {
    String protoTypeName = "." + protoPackage + "." + prefix + message.getName();
    String javaTypeName;

    if (multipleFiles) {
      javaTypeName = javaPackage + "." + prefix + message.getName();
    } else {
      javaTypeName = javaPackage + "." + outerClass + "." + prefix + message.getName();
    }

    typeToJavaMapping.put(protoTypeName, javaTypeName);

    // Map nested types
    for (DescriptorProtos.DescriptorProto nested : message.getNestedTypeList()) {
      mapMessage(nested, protoPackage, javaPackage, outerClass, multipleFiles, prefix + message.getName() + ".");
    }

    for (DescriptorProtos.EnumDescriptorProto nested : message.getEnumTypeList()) {
      mapEnum(nested, protoPackage, javaPackage, outerClass, multipleFiles, prefix + message.getName() + ".");
    }
  }

  private void mapEnum(DescriptorProtos.EnumDescriptorProto enumType, String protoPackage,
    String javaPackage, String outerClass, boolean multipleFiles, String prefix) {
    String protoTypeName = "." + protoPackage + "." + prefix + enumType.getName();
    String javaTypeName;

    if (multipleFiles) {
      javaTypeName = javaPackage + "." + prefix + enumType.getName();
    } else {
      javaTypeName = javaPackage + "." + outerClass + "." + prefix + enumType.getName();
    }

    typeToJavaMapping.put(protoTypeName, javaTypeName);
  }

  private String mapPrimitiveType(String protoType) {
    switch (protoType) {
      case "double":
        return "double";
      case "float":
        return "float";
      case "int64":
      case "uint64":
      case "fixed64":
      case "sfixed64":
      case "sint64":
        return "long";
      case "int32":
      case "fixed32":
      case "uint32":
      case "sfixed32":
      case "sint32":
        return "int";
      case "bool":
        return "boolean";
      case "string":
        return "java.lang.String";
      case "bytes":
        return "com.google.protobuf.ByteString";
      default:
        return protoType;
    }
  }

  private String constructJavaTypeName(String protoTypeName) {
    // Remove leading dot
    String typeName = protoTypeName.substring(1);

    // Split into package and type parts
    int lastDot = typeName.lastIndexOf('.');
    if (lastDot == -1) {
      return typeName;
    }

    String packagePart = typeName.substring(0, lastDot);
    String typePart = typeName.substring(lastDot + 1);

    // Convert package to Java package (this is a best guess)
    String javaPackage = packagePart.replace('_', '.');

    return javaPackage + "." + typePart;
  }

  private String toCamelCase(String input) {
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;

    for (char c : input.toCharArray()) {
      if (c == '_' || c == '-') {
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
}
