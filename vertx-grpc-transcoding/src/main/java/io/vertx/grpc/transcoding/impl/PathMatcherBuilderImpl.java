package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.HttpTemplate;
import io.vertx.grpc.transcoding.PathMatcher;
import io.vertx.grpc.transcoding.PathMatcherBuilder;
import io.vertx.grpc.transcoding.ServiceTranscodingOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PathMatcherBuilderImpl implements PathMatcherBuilder {
  private final PathMatcherNode root = new PathMatcherNode();
  private final Set<String> customVerbs = new HashSet<>();
  private final List<PathMatcherMethodData> methods = new ArrayList<>();

  private PercentEncoding.UrlUnescapeSpec pathUnescapeSpec = PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_RESERVED;
  private boolean queryParamUnescapePlus = false;
  private boolean matchUnregisteredCustomVerb = false;
  private boolean failRegistrationOnDuplicate = true;

  @Override
  public boolean register(ServiceTranscodingOptions transcoding, Set<String> queryParameterNames, String method) {
    HttpTemplate ht = transcoding.getHttpTemplate();
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

  @Override
  public boolean register(ServiceTranscodingOptions transcodingOptions, String method) {
    return register(transcodingOptions, new HashSet<>(), method);
  }

  @Override
  public PathMatcherNode getRoot() {
    return root;
  }

  @Override
  public Set<String> getCustomVerbs() {
    return customVerbs;
  }

  @Override
  public List<PathMatcherMethodData> getMethodData() {
    return methods;
  }

  @Override
  public void setUrlUnescapeSpec(PercentEncoding.UrlUnescapeSpec pathUnescapeSpec) {
    this.pathUnescapeSpec = pathUnescapeSpec;
  }

  @Override
  public PercentEncoding.UrlUnescapeSpec getUrlUnescapeSpec() {
    return pathUnescapeSpec;
  }

  @Override
  public void setQueryParamUnescapePlus(boolean queryParamUnescapePlus) {
    this.queryParamUnescapePlus = queryParamUnescapePlus;
  }

  @Override
  public boolean getQueryParamUnescapePlus() {
    return queryParamUnescapePlus;
  }

  @Override
  public void setMatchUnregisteredCustomVerb(boolean matchUnregisteredCustomVerb) {
    this.matchUnregisteredCustomVerb = matchUnregisteredCustomVerb;
  }

  @Override
  public boolean getMatchUnregisteredCustomVerb() {
    return matchUnregisteredCustomVerb;
  }

  @Override
  public void setFailRegistrationOnDuplicate(boolean failRegistrationOnDuplicate) {
    this.failRegistrationOnDuplicate = failRegistrationOnDuplicate;
  }

  @Override
  public boolean getFailRegistrationOnDuplicate() {
    return failRegistrationOnDuplicate;
  }

  @Override
  public PathMatcher build() {
    return new PathMatcherImpl(this);
  }

  private boolean insertPathToNode(PathMatcherNode.PathInfo path, Object data, String httpMethod, PathMatcherNode root) {
    if (!root.insertPath(path, httpMethod, data, true)) {
      if (failRegistrationOnDuplicate) {
        return false;
      }
    }
    return true;
  }
}
