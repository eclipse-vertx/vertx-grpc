package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.HttpTemplate;
import io.vertx.grpc.transcoding.HttpTemplateVariable;

import java.util.List;

public class HttpTemplateImpl implements HttpTemplate {

  private final List<String> segments;
  private final String verb;
  private final List<HttpTemplateVariable> variables;

  public HttpTemplateImpl(List<String> segments, String verb, List<HttpTemplateVariable> variables) {
    this.segments = segments;
    this.verb = verb;
    this.variables = variables;
  }

  @Override
  public List<String> getSegments() {
    return segments;
  }

  @Override
  public String getVerb() {
    return verb;
  }

  @Override
  public List<HttpTemplateVariable> getVariables() {
    return variables;
  }
}
