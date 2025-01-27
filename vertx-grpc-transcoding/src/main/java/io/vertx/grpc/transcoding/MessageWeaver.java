package io.vertx.grpc.transcoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MessageWeaver {

  public static Buffer weaveRequestMessage(Buffer message, List<HttpVariableBinding> bindings, String transcodingRequestBody) throws DecodeException {
    if (bindings.isEmpty() && transcodingRequestBody == null) {
      return message;
    }

    BufferInternal buffer = BufferInternal.buffer();
    JsonObject result = new JsonObject(message.toString());

    for (HttpVariableBinding binding : bindings) {
      JsonObject current = result;
      List<String> fieldPath = binding.getFieldPath();

      // Navigate to parent object, creating path if needed
      for (int i = 0; i < fieldPath.size() - 1; i++) {
        String fieldName = fieldPath.get(i);
        JsonObject next;
        if (!current.containsKey(fieldName)) {
          next = new JsonObject();
          current.put(fieldName, next);
        } else {
          Object existing = current.getValue(fieldName);
          if (!(existing instanceof JsonObject)) {
            next = new JsonObject();
            current.put(fieldName, next);
          } else {
            next = (JsonObject) existing;
          }
        }
        current = next;
      }

      // Set the value at the final path position
      current.put(fieldPath.get(fieldPath.size() - 1), binding.getValue());
    }

    // Handle transcoding request body
    if (transcodingRequestBody != null && !transcodingRequestBody.isEmpty()) {
      JsonObject messageJson = new JsonObject(message.toString());
      if (transcodingRequestBody.equals("*")) {
        result.mergeIn(messageJson);
      } else {
        JsonObject current = result;
        String[] path = transcodingRequestBody.split("\\.");
        for (int i = 0; i < path.length - 1; i++) {
          String fieldName = path[i];
          if (!current.containsKey(fieldName)) {
            current.put(fieldName, new JsonObject());
          }
          current = current.getJsonObject(fieldName);
        }
        current.put(path[path.length - 1], messageJson);
      }
    }

    if(transcodingRequestBody != null && !transcodingRequestBody.equals("*")) {
      String path = transcodingRequestBody.split("\\.")[0];
      result.fieldNames().removeIf(key -> !key.equals(path));
    }

    buffer.appendString(result.encode());
    return buffer;
  }

  public static Buffer weaveResponseMessage(Buffer message, String transcodingResponseBody) {
    if (transcodingResponseBody == null || transcodingResponseBody.isEmpty()) {
      return message;
    }

    JsonObject json = new JsonObject(message.toString());

    if (transcodingResponseBody.equals("*")) {
      return message;
    }

    String[] path = transcodingResponseBody.split("\\.");
    JsonObject current = json;

    // Navigate to the specified path
    for (String field : path) {
      if (current.containsKey(field)) {
        Object value = current.getValue(field);
        if (value instanceof JsonObject) {
          current = (JsonObject) value;
        } else {
          throw new IllegalStateException("Invalid transcodingResponseBody path: " + transcodingResponseBody);
        }
      } else {
        throw new IllegalStateException("Invalid transcodingResponseBody path: " + transcodingResponseBody);
      }
    }

    // Return just the object at the specified path
    return Buffer.buffer(current.encode());
  }
}
