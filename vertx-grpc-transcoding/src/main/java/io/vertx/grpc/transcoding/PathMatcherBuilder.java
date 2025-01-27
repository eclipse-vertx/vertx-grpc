package io.vertx.grpc.transcoding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builder interface for creating {@link PathMatcher} instances. Provides methods to configure and construct a path matcher with specific behaviors for HTTP-to-gRPC transcoding,
 * including URL encoding/decoding options and custom verb handling.
 */
public class PathMatcherBuilder {
  private final PathMatcherNode root = new PathMatcherNode();
  private final Set<String> customVerbs = new HashSet<>();
  private final List<PathMatcherMethodData> methods = new ArrayList<>();

  private PercentEncoding.UrlUnescapeSpec pathUnescapeSpec = PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_RESERVED;
  private boolean queryParamUnescapePlus = false;
  private boolean matchUnregisteredCustomVerb = false;
  private boolean failRegistrationOnDuplicate = true;

  /**
   * Registers a service transcoding configuration with specific query parameter names.
   *
   * @param transcoding the service transcoding options
   * @param queryParameterNames set of query parameter names to handle
   * @param method the gRPC method name to associate with this pattern
   * @return true if registration was successful, false otherwise
   */
  public boolean register(MethodTranscodingOptions transcoding, Set<String> queryParameterNames, String method) {
    HttpTemplate ht = HttpTemplate.parse(transcoding.getPath());
    if (ht == null) {
      return false;
    }

    PathMatcherNode.PathInfo info = PathMatcherUtility.transformHttpTemplate(ht);

    PathMatcherMethodData data = new PathMatcherMethodData();
    data.setMethod(method);
    data.setVariables(ht.getVariables());
    data.setBodyFieldPath(transcoding.getBody());
    data.setSystemQueryParameterNames(queryParameterNames);

    if (!insertPathToNode(info, data, transcoding.getHttpMethod() + ht.getVerb(), root)) {
      return false;
    }

    methods.add(data);

    if (!ht.getVerb().isEmpty()) {
      customVerbs.add(ht.getVerb());
    }

    return true;
  }

  /**
   * Registers a service transcoding configuration with default query parameter handling.
   *
   * @param transcodingOptions the service transcoding options
   * @param method the gRPC method name to associate with this pattern
   * @return true if registration was successful, false otherwise
   */
  public boolean register(MethodTranscodingOptions transcodingOptions, String method) {
    return register(transcodingOptions, new HashSet<>(), method);
  }

  private boolean insertPathToNode(PathMatcherNode.PathInfo path, Object data, String httpMethod, PathMatcherNode root) {
    if (!root.insertPath(path, httpMethod, data, true)) {
      if (failRegistrationOnDuplicate) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the root node of the path matching tree.
   *
   * @return the root PathMatcherNode
   */
  public PathMatcherNode root() {
    return root;
  }

  /**
   * Gets the set of registered custom verbs.
   *
   * @return set of custom verb strings
   */
  public Set<String> customVerbs() {
    return customVerbs;
  }

  /**
   * Gets the list of registered method data.
   *
   * @return list of PathMatcherMethodData objects
   */
  public List<PathMatcherMethodData> methodData() {
    return methods;
  }

  /**
   * Sets the URL unescaping specification for path variables.
   *
   * @param pathUnescapeSpec the URL unescaping specification to use
   */
  public void setUrlUnescapeSpec(PercentEncoding.UrlUnescapeSpec pathUnescapeSpec) {
    this.pathUnescapeSpec = pathUnescapeSpec;
  }

  /**
   * Gets the current URL unescaping specification.
   *
   * @return the current URL unescaping specification
   */
  public PercentEncoding.UrlUnescapeSpec getUrlUnescapeSpec() {
    return pathUnescapeSpec;
  }

  /**
   * Sets whether plus signs in query parameters should be unescaped to spaces.
   *
   * @param queryParamUnescapePlus true to unescape plus signs, false otherwise
   */
  public void setQueryParamUnescapePlus(boolean queryParamUnescapePlus) {
    this.queryParamUnescapePlus = queryParamUnescapePlus;
  }

  /**
   * Gets whether plus signs in query parameters are being unescaped to spaces.
   *
   * @return current setting for plus sign unescaping in query parameters
   */
  public boolean getQueryParamUnescapePlus() {
    return queryParamUnescapePlus;
  }

  /**
   * Sets whether unregistered custom verbs should be matched.
   *
   * @param matchUnregisteredCustomVerb true to match unregistered custom verbs, false otherwise
   */
  public void setMatchUnregisteredCustomVerb(boolean matchUnregisteredCustomVerb) {
    this.matchUnregisteredCustomVerb = matchUnregisteredCustomVerb;
  }

  /**
   * Gets whether unregistered custom verbs are being matched.
   *
   * @return current setting for matching unregistered custom verbs
   */
  public boolean getMatchUnregisteredCustomVerb() {
    return matchUnregisteredCustomVerb;
  }

  /**
   * Sets whether registration should fail when attempting to register a duplicate pattern.
   *
   * @param failRegistrationOnDuplicate true to fail on duplicates, false to silently continue
   */
  public void setFailRegistrationOnDuplicate(boolean failRegistrationOnDuplicate) {
    this.failRegistrationOnDuplicate = failRegistrationOnDuplicate;
  }

  /**
   * Gets whether registration fails on duplicate patterns.
   *
   * @return current setting for failing on duplicate registrations
   */
  public boolean getFailRegistrationOnDuplicate() {
    return failRegistrationOnDuplicate;
  }

  /**
   * Builds and returns a new PathMatcher instance based on the current configuration.
   *
   * @return a new PathMatcher instance
   */
  public PathMatcher build() {
    return new PathMatcher(this);
  }
}
