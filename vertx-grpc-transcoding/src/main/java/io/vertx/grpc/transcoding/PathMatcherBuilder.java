package io.vertx.grpc.transcoding;

import io.vertx.grpc.transcoding.impl.PathMatcherMethodData;
import io.vertx.grpc.transcoding.impl.PathMatcherNode;
import io.vertx.grpc.transcoding.impl.PercentEncoding;

import java.util.List;
import java.util.Set;

/**
 * Builder interface for creating {@link PathMatcher} instances. Provides methods to configure and construct a path matcher with specific behaviors for HTTP-to-gRPC transcoding,
 * including URL encoding/decoding options and custom verb handling.
 */
public interface PathMatcherBuilder {

  /**
   * Registers a service transcoding configuration with specific query parameter names.
   *
   * @param transcoding the service transcoding options
   * @param queryParameterNames set of query parameter names to handle
   * @param method the gRPC method name to associate with this pattern
   * @return true if registration was successful, false otherwise
   */
  boolean register(ServiceTranscodingOptions transcoding, Set<String> queryParameterNames, String method);

  /**
   * Registers a service transcoding configuration with default query parameter handling.
   *
   * @param transcodingOptions the service transcoding options
   * @param method the gRPC method name to associate with this pattern
   * @return true if registration was successful, false otherwise
   */
  boolean register(ServiceTranscodingOptions transcodingOptions, String method);

  /**
   * Gets the root node of the path matching tree.
   *
   * @return the root PathMatcherNode
   */
  PathMatcherNode getRoot();

  /**
   * Gets the set of registered custom verbs.
   *
   * @return set of custom verb strings
   */
  Set<String> getCustomVerbs();

  /**
   * Gets the list of registered method data.
   *
   * @return list of PathMatcherMethodData objects
   */
  List<PathMatcherMethodData> getMethodData();

  /**
   * Sets the URL unescaping specification for path variables.
   *
   * @param pathUnescapeSpec the URL unescaping specification to use
   */
  void setUrlUnescapeSpec(PercentEncoding.UrlUnescapeSpec pathUnescapeSpec);

  /**
   * Gets the current URL unescaping specification.
   *
   * @return the current URL unescaping specification
   */
  PercentEncoding.UrlUnescapeSpec getUrlUnescapeSpec();

  /**
   * Sets whether plus signs in query parameters should be unescaped to spaces.
   *
   * @param queryParamUnescapePlus true to unescape plus signs, false otherwise
   */
  void setQueryParamUnescapePlus(boolean queryParamUnescapePlus);

  /**
   * Gets whether plus signs in query parameters are being unescaped to spaces.
   *
   * @return current setting for plus sign unescaping in query parameters
   */
  boolean getQueryParamUnescapePlus();

  /**
   * Sets whether unregistered custom verbs should be matched.
   *
   * @param matchUnregisteredCustomVerb true to match unregistered custom verbs, false otherwise
   */
  void setMatchUnregisteredCustomVerb(boolean matchUnregisteredCustomVerb);

  /**
   * Gets whether unregistered custom verbs are being matched.
   *
   * @return current setting for matching unregistered custom verbs
   */
  boolean getMatchUnregisteredCustomVerb();

  /**
   * Sets whether registration should fail when attempting to register a duplicate pattern.
   *
   * @param failRegistrationOnDuplicate true to fail on duplicates, false to silently continue
   */
  void setFailRegistrationOnDuplicate(boolean failRegistrationOnDuplicate);

  /**
   * Gets whether registration fails on duplicate patterns.
   *
   * @return current setting for failing on duplicate registrations
   */
  boolean getFailRegistrationOnDuplicate();

  /**
   * Builds and returns a new PathMatcher instance based on the current configuration.
   *
   * @return a new PathMatcher instance
   */
  PathMatcher build();
}
