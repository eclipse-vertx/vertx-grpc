package io.vertx.grpc.eventbus.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

final class EventBusGrpcBody {

  private EventBusGrpcBody() {
  }

  static Buffer asBuffer(Object body) {
    if (body instanceof Buffer) {
      return (Buffer) body;
    }
    if (body instanceof JsonObject) {
      return ((JsonObject) body).toBuffer();
    }
    if (body == null) {
      return Buffer.buffer();
    }
    throw new IllegalStateException("Unsupported event bus body type: " + body.getClass().getName());
  }
}
