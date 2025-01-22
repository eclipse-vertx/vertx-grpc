package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.HttpTemplateVariable;

import java.util.List;
import java.util.Set;

public class PathMatcherMethodData {
  private String method;
  private List<HttpTemplateVariable> variables;
  private String bodyFieldPath;
  private Set<String> systemQueryParameterNames;

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public List<HttpTemplateVariable> getVariables() {
    return variables;
  }

  public void setVariables(List<HttpTemplateVariable> variables) {
    this.variables = variables;
  }

  public String getBodyFieldPath() {
    return bodyFieldPath;
  }

  public void setBodyFieldPath(String bodyFieldPath) {
    this.bodyFieldPath = bodyFieldPath;
  }

  public Set<String> getSystemQueryParameterNames() {
    return systemQueryParameterNames;
  }

  public void setSystemQueryParameterNames(Set<String> systemQueryParameterNames) {
    this.systemQueryParameterNames = systemQueryParameterNames;
  }
}
