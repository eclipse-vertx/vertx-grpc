package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.http.HttpMethod;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Defines configuration options for transcoding HTTP requests to gRPC name calls. This interface provides the necessary mapping information to translate between HTTP and gRPC,
 * including path templates, HTTP methods, body mappings, and additional bindings.
 *
 * The transcoding options define how incoming HTTP requests should be mapped to gRPC method calls, including:
 * <ul>
 *   <li>Which gRPC name/method to call (selector)</li>
 *   <li>What HTTP method and path pattern to match</li>
 *   <li>How to map the HTTP request/response bodies to gRPC messages</li>
 * </ul>
 */
@DataObject
@Unstable("Transcoding is in tech preview")
public class MethodTranscodingOptions {

  private String selector;
  private HttpMethod httpMethod;
  private String path;
  private String body;
  private String responseBody;
  private List<MethodTranscodingOptions> additionalBindings = new LinkedList<>();

  public MethodTranscodingOptions() {
    this.httpMethod = HttpMethod.GET;
  }

  public MethodTranscodingOptions(MethodTranscodingOptions that) {
    this.selector = that.selector;
    this.httpMethod = that.httpMethod;
    this.path = that.path;
    this.body = that.body;
    this.responseBody = that.responseBody;
    this.additionalBindings = new ArrayList<>(that.additionalBindings);
  }

  /**
   * Gets the fully-qualified name of the gRPC method to be called.
   *
   * @return The method selector string (e.g., "mypackage.MyService.MyMethod")
   */
  public String getSelector() {
    return selector;
  }

  /**
   * Set the fully-qualified name of the gRPC method to be called.
   *
   * @param selector the gRPC method
   * @return this instance
   */
  public MethodTranscodingOptions setSelector(String selector) {
    this.selector = selector;
    return this;
  }

  /**
   * Gets the HTTP method that this binding should match.
   *
   * @return The HTTP method (GET, POST, etc.)
   */
  public HttpMethod getHttpMethod() {
    return httpMethod;
  }

  /**
   * Sets the HTTP method that this binding should match.
   *
   * @param httpMethod the HTTP method
   * @return this instance
   */
  public MethodTranscodingOptions setHttpMethod(HttpMethod httpMethod) {
    this.httpMethod = httpMethod;
    return this;
  }

  /**
   * Gets the URL path template for this binding.
   *
   * @return The path template string (e.g., "/v1/users/{user_id}")
   */
  public String getPath() {
    return path;
  }

  /**
   * Sets the URL path template for this binding.
   *
   * @param path the path
   * @return this instance
   */
  public MethodTranscodingOptions setPath(String path) {
    this.path = path;
    return this;
  }

  /**
   * Gets the field path where the HTTP request body should be mapped in the gRPC request message.
   *
   * @return The body field path or null if no body mapping is needed
   */
  public String getBody() {
    return body;
  }

  /**
   * Sets the field path where the HTTP request body should be mapped in the gRPC request message.
   *
   * @param body the body
   * @return this instance
   */
  public MethodTranscodingOptions setBody(String body) {
    this.body = body;
    return this;
  }

  /**
   * Gets the field path in the gRPC response message to use as the HTTP response body.
   *
   * @return The response body field path or null if no response body mapping is needed
   */
  public String getResponseBody() {
    return responseBody;
  }

  /**
   * Sets the field path in the gRPC response message to use as the HTTP response body.
   *
   * @param responseBody the response doby
   * @return this instance
   */
  public MethodTranscodingOptions setResponseBody(String responseBody) {
    this.responseBody = responseBody;
    return this;
  }

  /**
   * Gets additional HTTP bindings for the same gRPC method. This allows a single gRPC method to be exposed through multiple HTTP endpoints.
   *
   * @return A list of additional bindings, or an empty list if none exist
   */
  public List<MethodTranscodingOptions> getAdditionalBindings() {
    return additionalBindings;
  }

  /**
   * Adds an additional binding.
   *
   * @param binding the binding.
   * @return this instance
   */
  public MethodTranscodingOptions addAdditionalBinding(MethodTranscodingOptions binding) {
    additionalBindings.add(binding);
    return this;
  }
}
