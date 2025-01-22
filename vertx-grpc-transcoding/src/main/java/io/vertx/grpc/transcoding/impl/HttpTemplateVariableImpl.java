package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.HttpTemplateVariable;

import java.util.ArrayList;
import java.util.List;

public class HttpTemplateVariableImpl implements HttpTemplateVariable {
  private List<String> fieldPath = new ArrayList<>();
  private int startSegment;
  private int endSegment;
  private boolean wildcardPath;

  @Override
  public List<String> getFieldPath() {
    return fieldPath;
  }

  @Override
  public void setFieldPath(List<String> fieldPath) {
    this.fieldPath = fieldPath;
  }

  @Override
  public int getStartSegment() {
    return startSegment;
  }

  @Override
  public void setStartSegment(int startSegment) {
    this.startSegment = startSegment;
  }

  @Override
  public int getEndSegment() {
    return endSegment;
  }

  @Override
  public void setEndSegment(int endSegment) {
    this.endSegment = endSegment;
  }

  @Override
  public boolean hasWildcardPath() {
    return wildcardPath;
  }

  @Override
  public void setWildcardPath(boolean wildcardPath) {
    this.wildcardPath = wildcardPath;
  }
}
