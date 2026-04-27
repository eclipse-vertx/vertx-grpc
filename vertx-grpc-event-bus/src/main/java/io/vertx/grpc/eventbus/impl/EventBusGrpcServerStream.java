package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;

public class EventBusGrpcServerStream extends EventBusGrpcStreamBase {

  private final Message<Object> eventBusMessage;
  private final WireFormat wireFormat;

  private GrpcMessage encodedMessage;
  private boolean replied;

  public EventBusGrpcServerStream(ContextInternal context, Message<Object> eventBusMessage, WireFormat wireFormat) {
    super(context);
    this.eventBusMessage = eventBusMessage;
    this.wireFormat = wireFormat;
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        return context.succeededFuture();
      case MESSAGE:
        encodedMessage = ((GrpcMessageFrame) frame).message();
        return context.succeededFuture();
      case TRAILERS:
        return handleTrailers((GrpcTrailersFrame) frame);
      default:
        return context.succeededFuture();
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame);
  }

  @Override
  public Future<Void> end() {
    return context.succeededFuture();
  }

  private Future<Void> handleTrailers(GrpcTrailersFrame frame) {
    if (replied) {
      return context.succeededFuture();
    }

    replied = true;

    GrpcStatus status = frame.status();
    if (status != GrpcStatus.OK) {
      String msg = frame.statusMessage() != null ? frame.statusMessage() : status.name();
      eventBusMessage.fail(status.code, msg);
    } else {
      Buffer payload = encodedMessage != null ? encodedMessage.payload() : Buffer.buffer();
      eventBusMessage.reply(wireFormat == WireFormat.JSON ? (payload.length() == 0 ? new JsonObject() : new JsonObject(payload)): payload);
    }
    return context.succeededFuture();
  }
}
