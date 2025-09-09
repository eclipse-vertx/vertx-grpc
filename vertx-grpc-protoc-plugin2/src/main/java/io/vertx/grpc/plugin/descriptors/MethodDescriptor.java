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

  /**
   * The default configuration for client streaming behavior. By default, client streaming is disabled.
   */
  public static final boolean DEFAULT_CLIENT_STREAMING = false;

  /**
   * The default configuration for server streaming behavior. By default, server streaming is disabled.
   */
  public static final boolean DEFAULT_SERVER_STREAMING = false;

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
  private boolean clientStreaming;
  private boolean serverStreaming;
  private boolean deprecated;
  private String documentation;
  private TranscodingDescriptor transcoding;
  private int methodNumber;
  private Map<String, Object> metadata;

  public MethodDescriptor() {
    this.name = DEFAULT_NAME;
    this.inputType = DEFAULT_INPUT_TYPE;
    this.outputType = DEFAULT_OUTPUT_TYPE;
    this.clientStreaming = DEFAULT_CLIENT_STREAMING;
    this.serverStreaming = DEFAULT_SERVER_STREAMING;
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
   * Determines whether this method uses client-side streaming.
   *
   * @return true if the method accepts a stream of requests from the client, false otherwise
   */
  public boolean isClientStreaming() {
    return clientStreaming;
  }

  /**
   * Sets whether this method uses client-side streaming.
   * <p>
   * Client streaming methods allow the client to send multiple request messages in a single method call, typically used for bulk operations or real-time data feeds.
   *
   * @param clientStreaming true to enable client streaming, false to disable
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setClientStreaming(boolean clientStreaming) {
    this.clientStreaming = clientStreaming;
    return this;
  }

  /**
   * Determines whether this method uses server-side streaming.
   *
   * @return true if the method returns a stream of responses to the client, false otherwise
   */
  public boolean isServerStreaming() {
    return serverStreaming;
  }

  /**
   * Sets whether this method uses server-side streaming.
   * <p>
   * Server streaming methods allow the server to send multiple response messages for a single request, typically used for data subscriptions or large result sets.
   *
   * @param serverStreaming true to enable server streaming, false to disable
   * @return the current instance of {@code MethodDescriptor} for method chaining
   */
  public MethodDescriptor setServerStreaming(boolean serverStreaming) {
    this.serverStreaming = serverStreaming;
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
    return NameUtils.mixedLower(name);
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
    if (!clientStreaming && !serverStreaming)
      return "oneToOne";
    if (!clientStreaming && serverStreaming)
      return "oneToMany";
    if (clientStreaming && !serverStreaming)
      return "manyToOne";
    return "manyToMany";
  }

  /**
   * Determines the appropriate gRPC method call type based on streaming characteristics.
   * <p>
   * This method returns the correct gRPC method name to use for async calls based on the combination of client and server streaming settings.
   *
   * @return the gRPC method call type: "asyncUnaryCall", "asyncServerStreamingCall", "asyncClientStreamingCall", or "asyncBidiStreamingCall"
   */
  public String getGrpcCallsMethodName() {
    if (!clientStreaming && !serverStreaming)
      return "asyncUnaryCall";
    if (!clientStreaming && serverStreaming)
      return "asyncServerStreamingCall";
    if (clientStreaming && !serverStreaming)
      return "asyncClientStreamingCall";
    return "asyncBidiStreamingCall";
  }
}
