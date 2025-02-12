package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.http.HttpMethod;

import java.util.LinkedList;
import java.util.List;

/**
 * Defines configuration options for transcoding HTTP requests to gRPC service calls. This interface provides the necessary mapping information to translate between HTTP and gRPC,
 * including path templates, HTTP methods, body mappings, and additional bindings.
 *
 * The transcoding options define how incoming HTTP requests should be mapped to gRPC method calls, including:
 * <ul>
 *   <li>Which gRPC service/method to call (selector)</li>
 *   <li>What HTTP method and path pattern to match</li>
 *   <li>How to map the HTTP request/response bodies to gRPC messages</li>
 * </ul>
 */
@DataObject
public class MethodTranscodingOptions {

  private final String selector;
  private final HttpMethod httpMethod;
  private final String path;
  private final String body;
  private final String responseBody;
  private final List<MethodTranscodingOptions> additionalBindings = new LinkedList<>();

  public MethodTranscodingOptions(String selector, HttpMethod httpMethod, String path, String body, String responseBody, List<MethodTranscodingOptions> additionalBindings) {
    this.selector = selector;
    this.httpMethod = httpMethod;
    this.path = path;
    this.body = body;
    this.responseBody = responseBody;
    if (additionalBindings != null) {
      this.additionalBindings.addAll(additionalBindings);
    }
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
   * Gets the HTTP method that this binding should match.
   *
   * @return The HTTP method (GET, POST, etc.)
   */
  public HttpMethod getHttpMethod() {
    return httpMethod;
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
   * Gets the field path where the HTTP request body should be mapped in the gRPC request message.
   *
   * @return The body field path or null if no body mapping is needed
   */
  public String getBody() {
    return body;
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
   * Gets additional HTTP bindings for the same gRPC method. This allows a single gRPC method to be exposed through multiple HTTP endpoints.
   *
   * @return A list of additional bindings, or an empty list if none exist
   */
  public List<MethodTranscodingOptions> getAdditionalBindings() {
    return additionalBindings;
  }
}
