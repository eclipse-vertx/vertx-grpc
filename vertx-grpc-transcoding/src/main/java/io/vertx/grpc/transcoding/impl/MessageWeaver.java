package io.vertx.grpc.transcoding.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;

import java.util.List;
import java.util.Objects;

/**
 * The MessageWeaver class handles the merging and transformation of gRPC messages during HTTP-to-gRPC transcoding operations.
 *
 * @see HttpVariableBinding
 */
public final class MessageWeaver {

  private static final String ROOT_LEVEL = "*";

  private MessageWeaver() {
  }

  /**
   * Weaves HTTP variable bindings and request body into a gRPC message.
   *
   * @param message The original message buffer
   * @param bindings The HTTP variable bindings
   * @param transcodingRequestBody The transcoding request body path
   * @return The modified buffer with weaved content
   * @throws DecodeException If JSON decoding fails
   */
  public static Buffer weaveRequestMessage(Buffer message, List<HttpVariableBinding> bindings, String transcodingRequestBody) throws DecodeException {
    if ((bindings == null || bindings.isEmpty()) && (transcodingRequestBody == null || transcodingRequestBody.isEmpty())) {
      return message;
    }

    JsonObject result = new JsonObject();

    if (bindings != null && !bindings.isEmpty()) {
      applyBindings(result, bindings);
    }

    JsonObject messageJson = null;
    if (message != null && !message.toString().isBlank()) {
      messageJson = message.toJsonObject();
    }

    if (messageJson != null && !messageJson.isEmpty()) {
      if (transcodingRequestBody == null || transcodingRequestBody.isEmpty()) {
        // No specific path, merge at root level with deep copy
        result.mergeIn(messageJson, true);
      } else if (ROOT_LEVEL.equals(transcodingRequestBody)) {
        // Wildcard, merge at root level without overwriting bindings
        result.mergeIn(messageJson);
      } else {
        applyAtPath(result, transcodingRequestBody.split("\\."), messageJson);
      }
    }

    return result.toBuffer();
  }

  /**
   * Applies HTTP variable bindings to the result object.
   */
  private static void applyBindings(JsonObject result, List<HttpVariableBinding> bindings) {
    for (HttpVariableBinding binding : bindings) {
      List<String> fieldPath = binding.getFieldPath();
      if (fieldPath == null || fieldPath.isEmpty()) {
        continue;
      }

      // Navigate to parent object, creating nested structure as needed
      JsonObject current = result;
      for (int i = 0; i < fieldPath.size() - 1; i++) {
        String fieldName = fieldPath.get(i);
        JsonObject next = current.getJsonObject(fieldName);
        if (next == null) {
          next = new JsonObject();
          current.put(fieldName, next);
        }
        current = next;
      }

      // Set the value at the final path position
      String lastField = fieldPath.get(fieldPath.size() - 1);
      current.put(lastField, binding.getValue());
    }
  }

  /**
   * Applies an object at a specific path in the JSON structure.
   */
  private static void applyAtPath(JsonObject root, String[] path, Object value) {
    JsonObject current = root;
    for (int i = 0; i < path.length - 1; i++) {
      String fieldName = path[i];
      JsonObject next = current.getJsonObject(fieldName);
      if (next == null) {
        next = new JsonObject();
        current.put(fieldName, next);
      }
      current = next;
    }
    current.put(path[path.length - 1], value);
  }

  /**
   * Extracts a response message portion based on the transcoding path.
   *
   * @param message The original message buffer
   * @param transcodingResponseBody The path to extract from the response
   * @return The modified buffer with the extracted content
   */
  public static Buffer weaveResponseMessage(Buffer message, String transcodingResponseBody) throws DecodeException {
    Objects.requireNonNull(message, "Message cannot be null");

    if (transcodingResponseBody == null || transcodingResponseBody.isEmpty() || transcodingResponseBody.equals(ROOT_LEVEL)) {
      return message;
    }

    JsonObject json = message.toJsonObject();
    String[] path = transcodingResponseBody.split("\\.");

    JsonObject current = json;
    for (String field : path) {
      Object value = current.getValue(field);
      if (value instanceof JsonObject) {
        current = (JsonObject) value;
      } else {
        throw new IllegalArgumentException("Path segment '" + field + "' in transcodingResponseBody does not refer to a JSON object");
      }
    }

    return current.toBuffer();
  }
}
