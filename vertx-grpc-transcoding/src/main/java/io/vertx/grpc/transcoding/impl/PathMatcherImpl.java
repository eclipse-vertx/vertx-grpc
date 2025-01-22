package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.HttpVariableBinding;
import io.vertx.grpc.transcoding.PathMatcher;
import io.vertx.grpc.transcoding.PathMatcherBuilder;
import io.vertx.grpc.transcoding.PathMatcherLookupResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PathMatcherImpl implements PathMatcher {
  private final PathMatcherNode root;
  private final Set<String> customVerbs = new HashSet<>();
  private final List<PathMatcherMethodData> methods = new ArrayList<>();
  private final PercentEncoding.UrlUnescapeSpec pathUnescapeSpec;
  private final boolean queryParamUnescapePlus;
  private final boolean matchUnregisteredCustomVerb;

  protected PathMatcherImpl(PathMatcherBuilder builder) {
    this.root = builder.getRoot().clone();
    this.customVerbs.addAll(builder.getCustomVerbs());
    this.methods.addAll(builder.getMethodData());
    this.pathUnescapeSpec = builder.getUrlUnescapeSpec();
    this.queryParamUnescapePlus = builder.getQueryParamUnescapePlus();
    this.matchUnregisteredCustomVerb = builder.getMatchUnregisteredCustomVerb();
  }

  @Override
  public String lookup(String httpMethod, String path) {
    PathMatcherLookupResult result = lookup(httpMethod, path, "");
    if (result == null) {
      return null;
    }

    return result.getMethod();
  }

  @Override
  public PathMatcherLookupResult lookup(String httpMethod, String path, String queryParams) {
    String verb = PathMatcherUtility.extractVerb(path, customVerbs, matchUnregisteredCustomVerb);
    List<String> parts = PathMatcherUtility.extractRequestParts(path, customVerbs, matchUnregisteredCustomVerb);
    if (root == null) {
      return null;
    }

    PathMatcherNode.PathMatcherLookupResult result = PathMatcherUtility.lookupInPathMatcherNode(root, parts, httpMethod + verb);

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
