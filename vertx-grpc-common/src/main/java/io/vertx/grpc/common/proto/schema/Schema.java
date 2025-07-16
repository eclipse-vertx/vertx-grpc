package io.vertx.grpc.common.proto.schema;

import java.util.HashMap;
import java.util.Map;

public class Schema {

  private final Map<String, MessageType> messages = new HashMap<>();

  public MessageType of(String messageName) {
    return messages.computeIfAbsent(messageName, MessageType::new);
  }

  public MessageType peek(String messageName) {
    return messages.get(messageName);
  }
}
