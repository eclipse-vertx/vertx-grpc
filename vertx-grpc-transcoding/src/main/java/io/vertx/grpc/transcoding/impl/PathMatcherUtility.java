package io.vertx.grpc.transcoding.impl;

import com.google.common.base.Splitter;
import io.vertx.grpc.transcoding.MethodTranscodingOptions;
import io.vertx.grpc.transcoding.impl.config.HttpTemplate;
import io.vertx.grpc.transcoding.impl.config.HttpTemplateVariable;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;

import java.util.*;

/**
 * Utility class for handling path matching operations in the gRPC transcoding implementation.
 *
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/include/grpc_transcoding/path_matcher.h">grpc-httpjson-transcoding</a>
 * @see PathMatcherBuilder
 * @see PathMatcherNode
 * @see MethodTranscodingOptions
 * @see HttpTemplate
 * @see HttpTemplateVariable
 */
public final class PathMatcherUtility {

  private static final Splitter PATH_SPLITTER = Splitter.on('/');
  private static final Splitter QUERY_PARAM_SPLITTER = Splitter.on('&');
  private static final Splitter NAME_SPLITTER = Splitter.on('.');

  private PathMatcherUtility() {
  }

  /**
   * Registers a method with the PathMatcherBuilder based on the provided transcoding options.
   *
   * @param pmb The PathMatcherBuilder to register with
   * @param transcodingOptions The transcoding options containing HTTP rules
   * @param method The method to register
   * @return true if registration was successful, false otherwise
   */
  public static boolean registerByHttpRule(PathMatcherBuilder pmb, MethodTranscodingOptions transcodingOptions, String method) {
    return registerByHttpRule(pmb, transcodingOptions, new HashSet<>(), method);
  }

  /**
   * Registers a method with the PathMatcherBuilder based on the provided transcoding options, with special handling for system query parameters.
   *
   * @param pmb The PathMatcherBuilder to register with
   * @param transcodingOptions The transcoding options containing HTTP rules
   * @param systemQueryParameterNames Set of query parameter names to be treated as system parameters
   * @param method The method to register
   * @return true if registration was successful, false otherwise
   */
  public static boolean registerByHttpRule(PathMatcherBuilder pmb, MethodTranscodingOptions transcodingOptions, Set<String> systemQueryParameterNames, String method) {
    Objects.requireNonNull(pmb, "PathMatcherBuilder cannot be null");
    Objects.requireNonNull(transcodingOptions, "MethodTranscodingOptions cannot be null");
    Objects.requireNonNull(systemQueryParameterNames, "System query parameter names set cannot be null");
    Objects.requireNonNull(method, "Method cannot be null");

    boolean ok = pmb.register(transcodingOptions, systemQueryParameterNames, method);

    // Handle additional bindings if they exist
    List<MethodTranscodingOptions> additionalBindings = transcodingOptions.getAdditionalBindings();
    if (additionalBindings == null || additionalBindings.isEmpty() || !ok) {
      return ok;
    }

    for (MethodTranscodingOptions binding : additionalBindings) {
      ok = registerByHttpRule(pmb, binding, systemQueryParameterNames, method);
      if (!ok) {
        return false;
      }
    }

    return true;
  }

  /**
   * Extracts variable bindings from a path based on template variables.
   *
   * @param vars The template variables defining where to extract values
   * @param parts The path parts to extract values from
   * @param unescapeSpec The URL unescaping specification to use
   * @return List of HTTP variable bindings extracted from the path
   */
  static List<HttpVariableBinding> extractBindingsFromPath(List<HttpTemplateVariable> vars, List<String> parts, PercentEncoding.UrlUnescapeSpec unescapeSpec) {
    if (vars == null || vars.isEmpty() || parts == null || parts.isEmpty()) {
      return Collections.emptyList();
    }

    List<HttpVariableBinding> bindings = new ArrayList<>(vars.size());
    int partsSize = parts.size();

    for (HttpTemplateVariable var : vars) {
      List<String> fieldPath = var.getFieldPath();
      HttpVariableBinding binding = new HttpVariableBinding(fieldPath, null);

      int startSegment = var.getStartSegment();
      int endSegment = var.getEndSegment() >= 0 ? var.getEndSegment() : partsSize + var.getEndSegment() + 1;

      // Skip invalid segment ranges
      if (startSegment < 0 || startSegment >= partsSize || endSegment <= startSegment) {
        continue;
      }

      // Adjust end segment if it exceeds parts size
      endSegment = Math.min(endSegment, partsSize);

      boolean multipart = (endSegment - startSegment) > 1 || var.getEndSegment() < 0;
      PercentEncoding.UrlUnescapeSpec spec = multipart ? unescapeSpec : PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS;

      StringBuilder valueBuilder = new StringBuilder();
      for (int i = startSegment; i < endSegment; ++i) {
        if (i > startSegment) {
          valueBuilder.append('/');
        }
        valueBuilder.append(PercentEncoding.urlUnescapeString(parts.get(i), spec, false));
      }

      binding.setValue(valueBuilder.toString());
      bindings.add(binding);
    }

    return bindings;
  }

  /**
   * Extracts variable bindings from query parameters.
   *
   * @param queryParams The query parameter string to extract from
   * @param systemParams Set of parameter names that should be treated as system parameters
   * @param queryParamUnescapePlus Whether to unescape plus signs in query parameters
   * @return List of HTTP variable bindings extracted from query parameters
   */
  static List<HttpVariableBinding> extractBindingsFromQueryParameters(String queryParams, Set<String> systemParams, boolean queryParamUnescapePlus) {
    if (queryParams == null || queryParams.isEmpty()) {
      return Collections.emptyList();
    }

    if (systemParams == null) {
      systemParams = Collections.emptySet();
    }

    List<HttpVariableBinding> bindings = new ArrayList<>();
    List<String> params = QUERY_PARAM_SPLITTER.splitToList(queryParams);

    for (String param : params) {
      int pos = param.indexOf('=');
      if (pos <= 0 || pos == param.length() - 1) {
        continue;
      }

      String name = param.substring(0, pos);
      if (systemParams.contains(name)) {
        continue;
      }

      String value = PercentEncoding.urlUnescapeString(param.substring(pos + 1), PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS, queryParamUnescapePlus);
      HttpVariableBinding binding = new HttpVariableBinding(NAME_SPLITTER.splitToList(name), value);

      bindings.add(binding);
    }

    return bindings;
  }

  /**
   * Extracts parts of a request path, handling query parameters and custom verbs.
   *
   * @param path The request path to extract parts from
   * @param customVerbs Set of custom verbs to recognize
   * @param matchUnregisteredCustomVerb Whether to match custom verbs that aren't in the customVerbs set
   * @return List of path segments extracted from the request path
   */
  static List<String> extractRequestParts(String path, Set<String> customVerbs, boolean matchUnregisteredCustomVerb) {
    if (path == null || path.isEmpty()) {
      return Collections.emptyList();
    }

    // Remove query parameters if present
    int queryIndex = path.indexOf('?');
    if (queryIndex != -1) {
      path = path.substring(0, queryIndex);
    }

    // Handle custom verbs
    path = removeCustomVerb(path, customVerbs, matchUnregisteredCustomVerb);

    // Skip leading slash and split path
    if (path.isEmpty()) {
      return Collections.emptyList();
    }

    String pathToSplit = path.charAt(0) == '/' ? path.substring(1) : path;
    List<String> result = new ArrayList<>(PATH_SPLITTER.splitToList(pathToSplit));

    // Remove trailing empty segments
    while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
      result.remove(result.size() - 1);
    }

    return result;
  }

  /**
   * Extracts a custom verb from a path if present.
   *
   * @param path The request path to extract the verb from
   * @param customVerbs Set of custom verbs to recognize
   * @param matchUnregisteredCustomVerb Whether to match custom verbs that aren't in the customVerbs set
   * @return The extracted verb, or an empty string if no verb was found
   */
  static String extractVerb(String path, Set<String> customVerbs, boolean matchUnregisteredCustomVerb) {
    if (path == null || path.isEmpty()) {
      return "";
    }

    // Remove query parameters if present
    int queryIndex = path.indexOf('?');
    if (queryIndex != -1) {
      path = path.substring(0, queryIndex);
    }

    // Check for custom verb
    int lastIndexOfColon = path.lastIndexOf(':');
    int lastIndexOfSlash = path.lastIndexOf('/');

    if (lastIndexOfColon != -1 && lastIndexOfColon > lastIndexOfSlash) {
      String verb = path.substring(lastIndexOfColon + 1);
      if (matchUnregisteredCustomVerb || (customVerbs != null && customVerbs.contains(verb))) {
        return verb;
      }
    }

    return "";
  }

  /**
   * Helper method to remove custom verb from a path if present.
   */
  private static String removeCustomVerb(String path, Set<String> customVerbs, boolean matchUnregisteredCustomVerb) {
    int lastIndexOfColon = path.lastIndexOf(':');
    int lastIndexOfSlash = path.lastIndexOf('/');

    if (lastIndexOfColon != -1 && lastIndexOfColon > lastIndexOfSlash) {
      String verb = path.substring(lastIndexOfColon + 1);
      if (matchUnregisteredCustomVerb || (customVerbs != null && customVerbs.contains(verb))) {
        return path.substring(0, lastIndexOfColon);
      }
    }

    return path;
  }

  /**
   * Looks up a path in a PathMatcherNode.
   *
   * @param root The root PathMatcherNode to start the lookup from
   * @param parts The path parts to look up
   * @param httpMethod The HTTP method to match
   * @return The lookup result
   */
  static PathMatcherNode.PathMatcherNodeLookupResult lookupInPathMatcherNode(PathMatcherNode root, List<String> parts, String httpMethod) {
    if (root == null) {
      return new PathMatcherNode.PathMatcherNodeLookupResult(null, false);
    }

    PathMatcherNode.PathMatcherNodeLookupResult result = new PathMatcherNode.PathMatcherNodeLookupResult(null, false);

    root.lookupPath(parts, 0, httpMethod, result);
    return result;
  }

  /**
   * Transforms an HttpTemplate into a PathInfo object.
   *
   * @param template The HttpTemplate to transform
   * @return The resulting PathInfo
   */
  static PathMatcherNode.PathInfo transformHttpTemplate(HttpTemplate template) {
    if (template == null || template.getSegments() == null) {
      return new PathMatcherNode.PathInfo.Builder().build();
    }

    PathMatcherNode.PathInfo.Builder builder = new PathMatcherNode.PathInfo.Builder();
    for (String part : template.getSegments()) {
      builder.appendLiteralNode(part);
    }
    return builder.build();
  }
}
