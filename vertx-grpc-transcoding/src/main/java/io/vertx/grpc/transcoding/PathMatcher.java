package io.vertx.grpc.transcoding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matches HTTP request paths against registered patterns to look up corresponding gRPC methods. This interface provides functionality to match incoming HTTP requests to their
 * corresponding gRPC method handlers based on the HTTP method, path, and optional query parameters.
 *
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/include/grpc_transcoding/path_matcher.h">grpc-httpjson-transcoding</a>
 */
public class PathMatcher {
  private final PathMatcherNode root;
  private final Set<String> customVerbs = new HashSet<>();
  private final List<PathMatcherMethodData> methods = new ArrayList<>();
  private final PercentEncoding.UrlUnescapeSpec pathUnescapeSpec;
  private final boolean queryParamUnescapePlus;
  private final boolean matchUnregisteredCustomVerb;

  protected PathMatcher(PathMatcherBuilder builder) {
    this.root = builder.root().clone();
    this.customVerbs.addAll(builder.customVerbs());
    this.methods.addAll(builder.methodData());
    this.pathUnescapeSpec = builder.getUrlUnescapeSpec();
    this.queryParamUnescapePlus = builder.getQueryParamUnescapePlus();
    this.matchUnregisteredCustomVerb = builder.getMatchUnregisteredCustomVerb();
  }

  /**
   * Simple lookup method that matches an HTTP request to a gRPC method based on HTTP method and path.
   *
   * @param httpMethod the HTTP method of the request (e.g., "GET", "POST")
   * @param path the request path to match
   * @return the corresponding gRPC method name if a match is found, null otherwise
   */
  public String lookup(String httpMethod, String path) {
    PathMatcherLookupResult result = lookup(httpMethod, path, "");
    if (result == null) {
      return null;
    }

    return result.getMethod();
  }

  /**
   * Advanced lookup method that matches an HTTP request to a gRPC method and extracts variable bindings.
   *
   * @param httpMethod the HTTP method of the request (e.g., "GET", "POST")
   * @param path the request path to match
   * @param queryParams the query parameters string from the request
   * @return the corresponding gRPC method name if a match is found, null otherwise
   */
  public PathMatcherLookupResult lookup(String httpMethod, String path, String queryParams) {
    String verb = PathMatcherUtility.extractVerb(path, customVerbs, matchUnregisteredCustomVerb);
    List<String> parts = PathMatcherUtility.extractRequestParts(path, customVerbs, matchUnregisteredCustomVerb);
    if (root == null) {
      return null;
    }

    PathMatcherNode.PathMatcherNodeLookupResult result = PathMatcherUtility.lookupInPathMatcherNode(root, parts, httpMethod + verb);

    if (result.getData() == null || result.isMultiple()) {
      return null;
    }

    PathMatcherMethodData data = (PathMatcherMethodData) result.getData();

    String method = data.getMethod();
    List<HttpVariableBinding> variableBindings = new ArrayList<>();
    String bodyFieldPath = data.getBodyFieldPath();

    variableBindings.addAll(PathMatcherUtility.extractBindingsFromPath(data.getVariables(), parts, pathUnescapeSpec));
    variableBindings.addAll(PathMatcherUtility.extractBindingsFromQueryParameters(queryParams, data.getSystemQueryParameterNames(), queryParamUnescapePlus));

    return new PathMatcherLookupResult(method, variableBindings, bodyFieldPath);
  }
}
