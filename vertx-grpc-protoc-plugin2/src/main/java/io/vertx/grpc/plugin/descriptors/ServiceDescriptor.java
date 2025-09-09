package io.vertx.grpc.plugin.descriptors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents metadata and configuration for a gRPC service definition.
 * <p>
 * This class encapsulates all the information needed to describe a gRPC service, including its name, package information, method definitions, deprecation status, and
 * documentation. It provides both basic metadata access and convenience methods for filtering methods by their streaming characteristics and transcoding configuration.
 * <p>
 * The descriptor supports various organizational features including Java package mapping, outer class configuration for generated code, and extensible metadata storage. It also
 * provides specialized access methods for different types of gRPC methods to facilitate template-based code generation.
 */
public class ServiceDescriptor {

  /**
   * The default service name when no specific name is provided. By default, no service name is specified.
   */
  public static final String DEFAULT_NAME = "";

  /**
   * The default package name for the gRPC service. By default, no package name is specified.
   */
  public static final String DEFAULT_PACKAGE_NAME = "";

  /**
   * The default Java package name for generated classes. By default, no Java package is specified.
   */
  public static final String DEFAULT_JAVA_PACKAGE = "";

  /**
   * The default outer class name for generated code organization. By default, no outer class is specified.
   */
  public static final String DEFAULT_OUTER_CLASS = "";

  /**
   * The default deprecation status for the service. By default, services are not marked as deprecated.
   */
  public static final boolean DEFAULT_DEPRECATED = false;

  /**
   * The default documentation string for the service. By default, no documentation is provided.
   */
  public static final String DEFAULT_DOCUMENTATION = "";

  private String name;
  private String packageName;
  private String javaPackage;
  private String outerClass;
  private List<MethodDescriptor> methods;
  private boolean deprecated;
  private String documentation;
  private Map<String, Object> metadata;

  public ServiceDescriptor() {
    this.name = DEFAULT_NAME;
    this.packageName = DEFAULT_PACKAGE_NAME;
    this.javaPackage = DEFAULT_JAVA_PACKAGE;
    this.outerClass = DEFAULT_OUTER_CLASS;
    this.methods = new ArrayList<>();
    this.deprecated = DEFAULT_DEPRECATED;
    this.documentation = DEFAULT_DOCUMENTATION;
    this.metadata = new HashMap<>();
  }

  /**
   * Retrieves the name of the gRPC service.
   *
   * @return the service name as a {@code String}
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the gRPC service.
   * <p>
   * The service name should correspond to the service name defined in the .proto file and is used for generating appropriate client and server code.
   *
   * @param name the service name to set
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Retrieves the package name as defined in the protobuf definition.
   *
   * @return the package name as a {@code String}
   */
  public String getPackageName() {
    return packageName;
  }

  /**
   * Sets the package name from the protobuf definition.
   * <p>
   * The package name corresponds to the 'package' declaration in the .proto file and is used for organizing generated code and avoiding naming conflicts.
   *
   * @param packageName the package name to set
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setPackageName(String packageName) {
    this.packageName = packageName;
    return this;
  }

  /**
   * Retrieves the Java package name for generated classes.
   *
   * @return the Java package name as a {@code String}
   */
  public String getJavaPackage() {
    return javaPackage;
  }

  /**
   * Sets the Java package name for generated classes.
   * <p>
   * The Java package name can be different from the protobuf package name and determines where the generated Java classes will be placed in the source tree. This is typically
   * configured via the java_package option in the .proto file.
   *
   * @param javaPackage the Java package name to set
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setJavaPackage(String javaPackage) {
    this.javaPackage = javaPackage;
    return this;
  }

  /**
   * Retrieves the outer class name for generated code organization.
   *
   * @return the outer class name as a {@code String}
   */
  public String getOuterClass() {
    return outerClass;
  }

  /**
   * Sets the outer class name for generated code organization.
   * <p>
   * The outer class name is used when multiple related classes need to be grouped together in a single compilation unit. This is typically configured via the java_outer_classname
   * option in the .proto file.
   *
   * @param outerClass the outer class name to set
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setOuterClass(String outerClass) {
    this.outerClass = outerClass;
    return this;
  }

  /**
   * Retrieves the list of method descriptors for this service.
   *
   * @return an immutable list of {@code MethodDescriptor} instances
   */
  public List<MethodDescriptor> getMethods() {
    return new ArrayList<>(methods);
  }

  /**
   * Adds a method descriptor to this service.
   * <p>
   * Methods are typically added in the order they appear in the .proto file to maintain consistency with the original service definition.
   *
   * @param method the method descriptor to add
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor addMethod(MethodDescriptor method) {
    this.methods.add(method);
    return this;
  }

  /**
   * Sets the complete list of method descriptors, replacing any existing methods.
   *
   * @param methods the list of method descriptors to set
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setMethods(List<MethodDescriptor> methods) {
    this.methods = new ArrayList<>(methods);
    return this;
  }

  /**
   * Determines whether this service is marked as deprecated.
   *
   * @return true if the service is deprecated, false otherwise
   */
  public boolean isDeprecated() {
    return deprecated;
  }

  /**
   * Sets the deprecation status of this service.
   * <p>
   * Deprecated services should not be used in new code and may be removed in future versions. This information can be used to generate appropriate annotations or warnings.
   *
   * @param deprecated true to mark the service as deprecated, false otherwise
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setDeprecated(boolean deprecated) {
    this.deprecated = deprecated;
    return this;
  }

  /**
   * Retrieves the documentation string for this service.
   *
   * @return the documentation as a {@code String}
   */
  public String getDocumentation() {
    return documentation;
  }

  /**
   * Sets the documentation string for this service.
   * <p>
   * The documentation typically contains descriptions, usage examples, or other relevant information about the service that can be included in generated code or API
   * documentation.
   *
   * @param documentation the documentation string to set
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setDocumentation(String documentation) {
    this.documentation = documentation;
    return this;
  }

  /**
   * Retrieves the metadata map containing additional service-specific information.
   *
   * @return a copy of the metadata map
   */
  public Map<String, Object> getMetadata() {
    return new HashMap<>(metadata);
  }

  /**
   * Adds a metadata entry with the specified key and value.
   * <p>
   * Metadata can be used to store additional information about the service that may be needed during code generation or runtime processing.
   *
   * @param key the metadata key
   * @param value the metadata value
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor addMetadata(String key, Object value) {
    this.metadata.put(key, value);
    return this;
  }

  /**
   * Sets the complete metadata map, replacing any existing metadata.
   *
   * @param metadata the metadata map to set
   * @return the current instance of {@code ServiceDescriptor} for method chaining
   */
  public ServiceDescriptor setMetadata(Map<String, Object> metadata) {
    this.metadata = new HashMap<>(metadata);
    return this;
  }

  /**
   * Retrieves all unary-to-unary methods (request-response pattern).
   * <p>
   * These are the most common gRPC methods where the client sends a single request and receives a single response. Neither client nor server streaming is used.
   *
   * @return a list of methods that are neither client streaming nor server streaming
   */
  public List<MethodDescriptor> getUnaryUnaryMethods() {
    return methods.stream()
      .filter(m -> !m.isClientStreaming() && !m.isServerStreaming())
      .collect(Collectors.toList());
  }

  /**
   * Retrieves all unary-to-stream methods (server streaming pattern).
   * <p>
   * These methods allow the client to send a single request and receive a stream of responses from the server. This pattern is useful for subscriptions or retrieving large
   * datasets.
   *
   * @return a list of methods that use server streaming but not client streaming
   */
  public List<MethodDescriptor> getUnaryStreamMethods() {
    return methods.stream()
      .filter(m -> !m.isClientStreaming() && m.isServerStreaming())
      .collect(Collectors.toList());
  }

  /**
   * Retrieves all stream-to-unary methods (client streaming pattern).
   * <p>
   * These methods allow the client to send a stream of requests and receive a single response from the server. This pattern is useful for bulk uploads or aggregation operations.
   *
   * @return a list of methods that use client streaming but not server streaming
   */
  public List<MethodDescriptor> getStreamUnaryMethods() {
    return methods.stream()
      .filter(m -> m.isClientStreaming() && !m.isServerStreaming())
      .collect(Collectors.toList());
  }

  /**
   * Retrieves all stream-to-stream methods (bidirectional streaming pattern).
   * <p>
   * These methods allow both the client and server to send streams of messages independently. This pattern is useful for real-time communication, chat applications, or interactive
   * data processing.
   *
   * @return a list of methods that use both client and server streaming
   */
  public List<MethodDescriptor> getStreamStreamMethods() {
    return methods.stream()
      .filter(m -> m.isClientStreaming() && m.isServerStreaming())
      .collect(Collectors.toList());
  }

  /**
   * Retrieves all methods that have transcoding configuration.
   * <p>
   * Transcoding methods can be accessed via HTTP/REST endpoints in addition to the standard gRPC protocol. This enables broader client compatibility and easier integration with
   * web-based applications.
   *
   * @return a list of methods that have transcoding descriptors configured
   */
  public List<MethodDescriptor> getTranscodingMethods() {
    return methods.stream()
      .filter(m -> m.getTranscoding() != null)
      .collect(Collectors.toList());
  }
}
