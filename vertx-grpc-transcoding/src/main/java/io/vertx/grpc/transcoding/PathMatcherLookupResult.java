package io.vertx.grpc.transcoding;

import java.util.List;

public class PathMatcherLookupResult {
  private final String method;
  private final List<HttpVariableBinding> variableBindings;
  private final String bodyFieldPath;

  public PathMatcherLookupResult(String method, List<HttpVariableBinding> variableBindings, String bodyFieldPath) {
    this.method = method;
    this.variableBindings = variableBindings;
    this.bodyFieldPath = bodyFieldPath;
  }

  public String getMethod() {
    return method;
  }

  public List<HttpVariableBinding> getVariableBindings() {
    return variableBindings;
  }

  public String getBodyFieldPath() {
    return bodyFieldPath;
  }
}
