package io.vertx.grpc.plugin.descriptors;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents transcoding configuration for HTTP/gRPC gateway functionality.
 * <p>
 * This class provides configuration options for transcoding gRPC services to RESTful HTTP APIs. It defines the mapping between HTTP requests and gRPC method calls, including path
 * patterns, HTTP methods, request/response body handling, and additional binding configurations.
 * <p>
 * The transcoding descriptor supports multiple bindings for a single gRPC method, allowing different HTTP endpoints to invoke the same underlying gRPC service method with
 * different parameter mappings and body configurations.
 */
public class TranscodingDescriptor {

  /**
   * The default HTTP path pattern for transcoding. By default, no path is specified.
   */
  public static final String DEFAULT_PATH = "";

  /**
   * The default HTTP method for transcoding operations. By default, no specific HTTP method is defined.
   */
  public static final String DEFAULT_METHOD = "";

  /**
   * The default selector for identifying the target gRPC method. By default, no selector is specified.
   */
  public static final String DEFAULT_SELECTOR = "";

  /**
   * The default request body configuration for transcoding. By default, no specific body handling is configured.
   */
  public static final String DEFAULT_BODY = "";

  /**
   * The default response body configuration for transcoding. By default, no specific response body handling is configured.
   */
  public static final String DEFAULT_RESPONSE_BODY = "";

  private String path;
  private String method;
  private String selector;
  private String body;
  private String responseBody;
  private List<TranscodingDescriptor> additionalBindings;

  public TranscodingDescriptor() {
    this.path = DEFAULT_PATH;
    this.method = DEFAULT_METHOD;
    this.selector = DEFAULT_SELECTOR;
    this.body = DEFAULT_BODY;
    this.responseBody = DEFAULT_RESPONSE_BODY;
    this.additionalBindings = new ArrayList<>();
  }

  /**
   * Retrieves the HTTP path pattern used for routing requests to the gRPC method.
   *
   * @return the HTTP path pattern as a {@code String}
   */
  public String getPath() {
    return path;
  }

  /**
   * Sets the HTTP path pattern for transcoding routing.
   * <p>
   * The path pattern defines how HTTP requests are matched and routed to the corresponding gRPC method. It may include parameter placeholders for extracting values from the URL.
   *
   * @param path the HTTP path pattern to set
   * @return the current instance of {@code TranscodingDescriptor} for method chaining
   */
  public TranscodingDescriptor setPath(String path) {
    this.path = path;
    return this;
  }

  /**
   * Retrieves the HTTP method (GET, POST, PUT, DELETE, etc.) used for the transcoding operation.
   *
   * @return the HTTP method as a {@code String}
   */
  public String getMethod() {
    return method;
  }

  /**
   * Sets the HTTP method for the transcoding operation.
   * <p>
   * The HTTP method determines how the HTTP client should invoke the transcoded gRPC service. Common values include "GET", "POST", "PUT", "DELETE", "PATCH", etc.
   *
   * @param method the HTTP method to set
   * @return the current instance of {@code TranscodingDescriptor} for method chaining
   */
  public TranscodingDescriptor setMethod(String method) {
    this.method = method;
    return this;
  }

  /**
   * Retrieves the selector that identifies the target gRPC method for this transcoding configuration.
   *
   * @return the method selector as a {@code String}
   */
  public String getSelector() {
    return selector;
  }

  /**
   * Sets the selector for identifying the target gRPC method.
   * <p>
   * The selector typically corresponds to the fully qualified method name in the gRPC service definition, used to establish the mapping between HTTP requests and gRPC method
   * calls.
   *
   * @param selector the method selector to set
   * @return the current instance of {@code TranscodingDescriptor} for method chaining
   */
  public TranscodingDescriptor setSelector(String selector) {
    this.selector = selector;
    return this;
  }

  /**
   * Retrieves the request body configuration for the transcoding operation.
   *
   * @return the request body configuration as a {@code String}
   */
  public String getBody() {
    return body;
  }

  /**
   * Sets the request body configuration for transcoding.
   * <p>
   * This configuration defines how the HTTP request body should be mapped to the gRPC method parameters. Special values like "*" indicate that the entire request body should be
   * used as the method input.
   *
   * @param body the request body configuration to set
   * @return the current instance of {@code TranscodingDescriptor} for method chaining
   */
  public TranscodingDescriptor setBody(String body) {
    this.body = body;
    return this;
  }

  /**
   * Retrieves the response body configuration for the transcoding operation.
   *
   * @return the response body configuration as a {@code String}
   */
  public String getResponseBody() {
    return responseBody;
  }

  /**
   * Sets the response body configuration for transcoding.
   * <p>
   * This configuration defines how the gRPC method response should be mapped to the HTTP response body. It can specify which fields from the gRPC response should be included in
   * the HTTP response.
   *
   * @param responseBody the response body configuration to set
   * @return the current instance of {@code TranscodingDescriptor} for method chaining
   */
  public TranscodingDescriptor setResponseBody(String responseBody) {
    this.responseBody = responseBody;
    return this;
  }

  /**
   * Retrieves the list of additional bindings for this transcoding configuration.
   * <p>
   * Additional bindings allow multiple HTTP endpoints to map to the same gRPC method with different parameter mappings or body configurations.
   *
   * @return an immutable list of additional {@code TranscodingDescriptor} bindings
   */
  public List<TranscodingDescriptor> getAdditionalBindings() {
    return new ArrayList<>(additionalBindings);
  }

  /**
   * Adds an additional binding configuration to this transcoding descriptor.
   * <p>
   * Additional bindings enable multiple HTTP endpoints to invoke the same gRPC method with different routing patterns, HTTP methods, or body configurations.
   *
   * @param binding the additional transcoding binding to add
   * @return the current instance of {@code TranscodingDescriptor} for method chaining
   */
  public TranscodingDescriptor addAdditionalBinding(TranscodingDescriptor binding) {
    this.additionalBindings.add(binding);
    return this;
  }

  /**
   * Sets the complete list of additional bindings, replacing any existing bindings.
   *
   * @param additionalBindings the list of additional transcoding bindings to set
   * @return the current instance of {@code TranscodingDescriptor} for method chaining
   */
  public TranscodingDescriptor setAdditionalBindings(List<TranscodingDescriptor> additionalBindings) {
    this.additionalBindings = new ArrayList<>(additionalBindings);
    return this;
  }
}
