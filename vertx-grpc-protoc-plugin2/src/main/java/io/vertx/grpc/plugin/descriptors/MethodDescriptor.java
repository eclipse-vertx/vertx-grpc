package io.vertx.grpc.plugin.descriptors;

import io.vertx.grpc.plugin.generation.context.NameUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents metadata and configuration for a gRPC service method.
 * <p>
 * This class encapsulates all the information needed to describe a gRPC method, including its name, input and output types, streaming characteristics, deprecation status, and
 * transcoding configuration. It provides both basic metadata access and convenience methods for code generation templates.
 * <p>
 * The descriptor supports various gRPC method types including unary, client streaming, server streaming, and bidirectional streaming methods. It also includes support for HTTP
 * transcoding configuration and method-specific metadata storage.
 */
public class MethodDescriptor {

  /**
   * The default method name when no specific name is provided. By default, no method name is specified.
   */
  public static final String DEFAULT_NAME = "";

  /**
   * The default input type for the gRPC method. By default, no input type is specified.
   */
  public static final String DEFAULT_INPUT_TYPE = "";

  /**
   * The default output type for the gRPC method. By default, no output type is specified.
   */
  public static final String DEFAULT_OUTPUT_TYPE = "";

  public static final String UNARY = "UNARY";
  public static final String CLIENT_STREAMING = "CLIENT_STREAMING";
  public static final String SERVER_STREAMING = "SERVER_STREAMING";
  public static final String BIDI_STREAMING = "BIDI_STREAMING";

  /**
   * The default cardinality for the method. By default, methods are bidi.
   */
  public static final String DEFAULT_CARDINALITY = BIDI_STREAMING;

  /**
   * The default deprecation status for the method. By default, methods are not marked as deprecated.
   */
  public static final boolean DEFAULT_DEPRECATED = false;

  /**
   * The default documentation string for the method. By default, no documentation is provided.
   */
  public static final String DEFAULT_DOCUMENTATION = "";

  /**
   * The default method number for ordering and identification purposes. By default, method number is 0.
   */
  public static final int DEFAULT_METHOD_NUMBER = 0;

  private String name;
  private String inputType;
  private String outputType;
  private String cardinality;
  private boolean deprecated;
  private String documentation;
  private TranscodingDescriptor transcoding;
  private int methodNumber;
  private Map<String, Object> metadata;

  public MethodDescriptor() {
    this.name = DEFAULT_NAME;
    this.inputType = DEFAULT_INPUT_TYPE;
    this.outputType = DEFAULT_OUTPUT_TYPE;
    this.cardinality = DEFAULT_CARDINALITY;
    this.deprecated = DEFAULT_DEPRECATED;
    this.documentation = DEFAULT_DOCUMENTATION;
    this.methodNumber = DEFAULT_METHOD_NUMBER;
    this.metadata = new HashMap<>();
  }

  /**
   * Retrieves the name of the gRPC method.
   *
   * @return the method name as a {@code String}
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the gRPC method.
   * <p>
   * The method name should correspond to the method name defined in the .proto file and is used for generating appropriate client and server code.
   *
   * @param name the method name to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Retrieves the fully qualified input type name for the gRPC method.
   *
   * @return the input type name as a {@code String}
   */
  public String getInputType() {
    return inputType;
  }

  /**
   * Sets the fully qualified input type name for the gRPC method.
   * <p>
   * The input type should be the complete class name including package information for the protobuf message type used as the method's request parameter.
   *
   * @param inputType the input type name to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setInputType(String inputType) {
    this.inputType = inputType;
    return this;
  }

  /**
   * Retrieves the fully qualified output type name for the gRPC method.
   *
   * @return the output type name as a {@code String}
   */
  public String getOutputType() {
    return outputType;
  }

  /**
   * Sets the fully qualified output type name for the gRPC method.
   * <p>
   * The output type should be the complete class name including package information for the protobuf message type used as the method's response.
   *
   * @param outputType the output type name to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setOutputType(String outputType) {
    this.outputType = outputType;
    return this;
  }

  /**
   * Retrieves the cardinality of this gRPC method.
   *
   * @return the method cardinality
   */
  public String getCardinality() {
    return cardinality;
  }

  /**
   * Sets the cardinality of this gRPC method.
   *
   * @param cardinality the method cardinality to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setCardinality(String cardinality) {
    this.cardinality = cardinality;
    return this;
  }

  /**
   * Determines whether this method is marked as deprecated.
   *
   * @return true if the method is deprecated, false otherwise
   */
  public boolean isDeprecated() {
    return deprecated;
  }

  /**
   * Sets the deprecation status of this method.
   * <p>
   * Deprecated methods should not be used in new code and may be removed in future versions. This information can be used to generate appropriate annotations or warnings.
   *
   * @param deprecated true to mark the method as deprecated, false otherwise
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setDeprecated(boolean deprecated) {
    this.deprecated = deprecated;
    return this;
  }

  /**
   * Retrieves the documentation string for this method.
   *
   * @return the documentation as a {@code String}
   */
  public String getDocumentation() {
    return documentation;
  }

  /**
   * Sets the documentation string for this method.
   * <p>
   * The documentation typically contains descriptions, usage examples, or other relevant information about the method that can be included in generated code or API documentation.
   *
   * @param documentation the documentation string to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setDocumentation(String documentation) {
    this.documentation = documentation;
    return this;
  }

  /**
   * Retrieves the transcoding configuration for this method.
   *
   * @return the transcoding descriptor, or null if transcoding is not configured
   */
  public TranscodingDescriptor getTranscoding() {
    return transcoding;
  }

  /**
   * Sets the transcoding configuration for HTTP/gRPC gateway functionality.
   * <p>
   * The transcoding configuration enables this gRPC method to be accessible via RESTful HTTP endpoints, with automatic translation between HTTP requests/responses and gRPC method
   * calls.
   *
   * @param transcoding the transcoding descriptor to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setTranscoding(TranscodingDescriptor transcoding) {
    this.transcoding = transcoding;
    return this;
  }

  /**
   * Retrieves the method number used for ordering and identification purposes.
   *
   * @return the method number as an {@code int}
   */
  public int getMethodNumber() {
    return methodNumber;
  }

  /**
   * Sets the method number for ordering and identification purposes.
   * <p>
   * The method number can be used for consistent ordering of methods in generated code or for creating unique identifiers within the service scope.
   *
   * @param methodNumber the method number to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setMethodNumber(int methodNumber) {
    this.methodNumber = methodNumber;
    return this;
  }

  /**
   * Retrieves the metadata map containing additional method-specific information.
   *
   * @return a copy of the metadata map
   */
  public Map<String, Object> getMetadata() {
    return new HashMap<>(metadata);
  }

  /**
   * Adds a metadata entry with the specified key and value.
   * <p>
   * Metadata can be used to store additional information about the method that may be needed during code generation or runtime processing.
   *
   * @param key the metadata key
   * @param value the metadata value
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor addMetadata(String key, Object value) {
    this.metadata.put(key, value);
    return this;
  }

  /**
   * Sets the complete metadata map, replacing any existing metadata.
   *
   * @param metadata the metadata map to set
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setMetadata(Map<String, Object> metadata) {
    this.metadata = new HashMap<>(metadata);
    return this;
  }

  /**
   * Retrieves the method name formatted for Vert.x conventions.
   * <p>
   * This method converts the original method name to mixed case (camelCase) formatting suitable for use in Vert.x-generated code.
   *
   * @return the method name in mixed case format
   */
  public String getVertxMethodName() {
    return NameUtils.formatMethodName(name);
  }

  /**
   * Retrieves the method name formatted in uppercase with underscores.
   * <p>
   * This formatting is typically used for constants or enum values in generated code.
   *
   * @return the method name in UPPER_UNDERSCORE format
   */
  public String getMethodNameUpperUnderscore() {
    return NameUtils.toUpperUnderscore(name);
  }

  /**
   * Determines the appropriate Vert.x method call type based on streaming characteristics.
   * <p>
   * This method returns the correct method name to use when calling Vert.x gRPC methods based on the combination of client and server streaming settings.
   *
   * @return the Vert.x method call type: "oneToOne", "oneToMany", "manyToOne", or "manyToMany"
   */
  public String getVertxCallsMethodName() {
    switch (cardinality) {
      case UNARY:
        return "oneToOne";
      case SERVER_STREAMING:
        return "oneToMany";
      case CLIENT_STREAMING:
        return "manyToOne";
      case BIDI_STREAMING:
        return "manyToMany";
      default:
        throw new IllegalStateException("Unknown cardinality: " + cardinality);
    }
  }

  /**
   * Determines the appropriate gRPC method call type based on streaming characteristics.
   * <p>
   * This method returns the correct gRPC method name to use for async calls based on the combination of client and server streaming settings.
   *
   * @return the gRPC method call type: "asyncUnaryCall", "asyncServerStreamingCall", "asyncClientStreamingCall", or "asyncBidiStreamingCall"
   */
  public String getGrpcCallsMethodName() {
    switch (cardinality) {
      case UNARY:
        return "asyncUnaryCall";
      case SERVER_STREAMING:
        return "asyncServerStreamingCall";
      case CLIENT_STREAMING:
        return "asyncClientStreamingCall";
      case BIDI_STREAMING:
        return "asyncBidiStreamingCall";
      default:
        throw new IllegalStateException("Unknown cardinality: " + cardinality);
    }
  }
}
