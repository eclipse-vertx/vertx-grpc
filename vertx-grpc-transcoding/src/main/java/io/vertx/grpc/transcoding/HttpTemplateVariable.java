package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.grpc.transcoding.impl.HttpTemplateVariableImpl;

import java.util.List;

/**
 * Represents a variable within an HTTP template used in gRPC transcoding.
 * <p>
 * This interface defines methods for accessing information about a variable, such as its field path, start and end segments, and whether it represents a wildcard path.
 *
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/http_template.cc">grpc-httpjson-transcoding</a>
 */
@VertxGen
public interface HttpTemplateVariable {

  /**
   * Creates a new {@code HttpTemplateVariable} instance.
   *
   * @param fieldPath The field path of the variable.
   * @param startSegment The starting segment of the variable.
   * @param endSegment The ending segment of the variable.
   * @param wildcardPath {@code true} if the variable represents a wildcard path, {@code false} otherwise.
   * @return The created {@code HttpTemplateVariable} instance.
   */
  static HttpTemplateVariable create(List<String> fieldPath, int startSegment, int endSegment, boolean wildcardPath) {
    HttpTemplateVariableImpl variable = new HttpTemplateVariableImpl();
    variable.setFieldPath(fieldPath);
    variable.setStartSegment(startSegment);
    variable.setEndSegment(endSegment);
    variable.setWildcardPath(wildcardPath);
    return variable;
  }

  /**
   * Returns the field path of the variable.
   *
   * @return The field path.
   */
  List<String> getFieldPath();

  /**
   * Sets the field path of the variable.
   *
   * @param fieldPath The field path to set.
   */
  void setFieldPath(List<String> fieldPath);

  /**
   * Returns the starting segment of the variable.
   *
   * @return The starting segment.
   */
  int getStartSegment();

  /**
   * Sets the starting segment of the variable.
   *
   * @param startSegment The starting segment to set.
   */
  void setStartSegment(int startSegment);

  /**
   * Returns the ending segment of the variable.
   *
   * @return The ending segment.
   */
  int getEndSegment();

  /**
   * Sets the ending segment of the variable.
   *
   * @param endSegment The ending segment to set.
   */
  void setEndSegment(int endSegment);

  /**
   * Checks if the variable represents a wildcard path.
   *
   * @return {@code true} if the variable represents a wildcard path, {@code false} otherwise.
   */
  boolean hasWildcardPath();

  /**
   * Sets whether the variable represents a wildcard path.
   *
   * @param wildcardPath {@code true} if the variable represents a wildcard path, {@code false} otherwise.
   */
  void setWildcardPath(boolean wildcardPath);
}
