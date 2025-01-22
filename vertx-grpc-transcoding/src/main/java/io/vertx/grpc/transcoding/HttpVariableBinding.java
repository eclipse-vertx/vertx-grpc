package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.grpc.transcoding.impl.HttpVariableBindingImpl;

import java.util.List;

/**
 * Represents a binding between an HTTP request variable (from path or query parameters)
 * and its corresponding gRPC message field path. This interface is used during HTTP-to-gRPC
 * transcoding to map HTTP request variables to their appropriate locations in the gRPC message.
 *
 * The binding consists of:
 * - A field path representing the location in the gRPC message where the value should be placed
 * - The actual value extracted from the HTTP request
 */
@VertxGen
public interface HttpVariableBinding {

  /**
   * Creates a new HttpVariableBinding instance.
   *
   * @param fieldPath A list of field names representing the path in the gRPC message
   *                  where the value should be placed. For example, ["user", "address", "city"]
   *                  would represent a path to the city field in a nested message structure
   * @param value The value extracted from the HTTP request that should be bound to this location
   * @return A new HttpVariableBinding instance
   */
  static HttpVariableBinding create(List<String> fieldPath, String value) {
    return new HttpVariableBindingImpl(fieldPath, value);
  }

  /**
   * Gets the field path that describes where in the gRPC message the value should be placed.
   *
   * @return A list of field names representing the path in the gRPC message
   */
  List<String> getFieldPath();

  /**
   * Sets the field path that describes where in the gRPC message the value should be placed.
   *
   * @param fieldPath A list of field names representing the path in the gRPC message
   */
  void setFieldPath(List<String> fieldPath);

  /**
   * Gets the value that was extracted from the HTTP request.
   *
   * @return The string value to be bound to the specified field path
   */
  String getValue();

  /**
   * Sets the value that should be bound to the specified field path.
   *
   * @param value The string value to be bound to the specified field path
   */
  void setValue(String value);
}
