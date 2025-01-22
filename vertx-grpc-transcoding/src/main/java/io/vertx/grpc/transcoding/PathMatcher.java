package io.vertx.grpc.transcoding;

/**
 * Matches HTTP request paths against registered patterns to look up corresponding gRPC methods. This interface provides functionality to match incoming HTTP requests to their
 * corresponding gRPC method handlers based on the HTTP method, path, and optional query parameters.
 */
public interface PathMatcher {

  /**
   * Simple lookup method that matches an HTTP request to a gRPC method based on HTTP method and path.
   *
   * @param httpMethod the HTTP method of the request (e.g., "GET", "POST")
   * @param path the request path to match
   * @return the corresponding gRPC method name if a match is found, null otherwise
   */
  String lookup(String httpMethod, String path);

  /**
   * Advanced lookup method that matches an HTTP request to a gRPC method and extracts variable bindings.
   *
   * @param httpMethod the HTTP method of the request (e.g., "GET", "POST")
   * @param path the request path to match
   * @param queryParams the query parameters string from the request
   * @return the corresponding gRPC method name if a match is found, null otherwise
   */
  PathMatcherLookupResult lookup(String httpMethod, String path, String queryParams);
}
