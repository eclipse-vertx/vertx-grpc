package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.http.HttpMethod;

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
@VertxGen
public interface ServiceTranscodingOptions {

  /**
   * Creates a new ServiceTranscodingOptions instance with all configuration options.
   *
   * @param selector The fully-qualified name of the gRPC method (e.g., "mypackage.MyService.MyMethod")
   * @param httpMethod The HTTP method to match (GET, POST, etc.)
   * @param path The URL path template with optional variables (e.g., "/v1/users/{user_id}")
   * @param body The field path where the HTTP request body should be mapped in the gRPC request message
   * @param responseBody The field path in the gRPC response message to use as the HTTP response body
   * @param additionalBindings Additional HTTP bindings for the same gRPC method
   * @return A new ServiceTranscodingOptions instance
   */
  static ServiceTranscodingOptions create(String selector, HttpMethod httpMethod, String path, String body, String responseBody,
    List<ServiceTranscodingOptions> additionalBindings) {
    return new ServiceTranscodingOptions() {
      @Override
      public String getSelector() {
        return selector;
      }

      @Override
      public HttpMethod getHttpMethod() {
        return httpMethod;
      }

      @Override
      public String getPath() {
        return path;
      }

      @Override
      public String getBody() {
        return body;
      }

      @Override
      public String getResponseBody() {
        return responseBody;
      }

      @Override
      public List<ServiceTranscodingOptions> getAdditionalBindings() {
        return additionalBindings == null ? List.of() : additionalBindings;
      }
    };
  }

  /**
   * Creates a simple binding without additional bindings.
   *
   * @param selector The fully-qualified name of the gRPC method
   * @param httpMethod The HTTP method to match
   * @param path The URL path template
   * @param body The request body field path
   * @param responseBody The response body field path
   * @return A new ServiceTranscodingOptions instance
   */
  static ServiceTranscodingOptions createBinding(String selector, HttpMethod httpMethod, String path, String body, String responseBody) {
    return create(selector, httpMethod, path, body, responseBody, null);
  }

  /**
   * Gets the fully-qualified name of the gRPC method to be called.
   *
   * @return The method selector string (e.g., "mypackage.MyService.MyMethod")
   */
  String getSelector();

  /**
   * Gets the HTTP method that this binding should match.
   *
   * @return The HTTP method (GET, POST, etc.)
   */
  HttpMethod getHttpMethod();

  /**
   * Gets the URL path template for this binding.
   *
   * @return The path template string (e.g., "/v1/users/{user_id}")
   */
  String getPath();

  /**
   * Parses the path template into a structured HttpTemplate object.
   *
   * @return An HttpTemplate instance representing the parsed path
   */
  default HttpTemplate getHttpTemplate() {
    return HttpTemplate.parse(getPath());
  }

  /**
   * Gets the field path where the HTTP request body should be mapped in the gRPC request message.
   *
   * @return The body field path or null if no body mapping is needed
   */
  String getBody();

  /**
   * Gets the field path in the gRPC response message to use as the HTTP response body.
   *
   * @return The response body field path or null if no response body mapping is needed
   */
  String getResponseBody();

  /**
   * Gets additional HTTP bindings for the same gRPC method. This allows a single gRPC method to be exposed through multiple HTTP endpoints.
   *
   * @return A list of additional bindings, or an empty list if none exist
   */
  List<ServiceTranscodingOptions> getAdditionalBindings();
}
