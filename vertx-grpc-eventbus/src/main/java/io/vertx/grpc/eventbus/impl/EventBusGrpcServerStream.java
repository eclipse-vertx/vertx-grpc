package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

public class EventBusGrpcServerStream extends EventBusGrpcStreamBase {

  private final Message<Object> eventBusMessage;
  private final WireFormat wireFormat;

  private GrpcMessage encodedMessage;
  private boolean replied;
  private MultiMap headers;

  public EventBusGrpcServerStream(ContextInternal context, Message<Object> eventBusMessage, WireFormat wireFormat) {
    super(context);
    this.eventBusMessage = eventBusMessage;
    this.wireFormat = wireFormat;
  }


  @Override
  public Future<Void> write(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
        headers = headersFrame.headers();
        return context.succeededFuture();
      case MESSAGE:
        encodedMessage = ((GrpcMessageFrame) frame).message();
        return context.succeededFuture();
      case TRAILERS:
        assert !replied;
        replied = true;
        GrpcTrailersFrame trailersFrame = (GrpcTrailersFrame) frame;
        return handleTrailers(
          trailersFrame.status(),
          trailersFrame.statusMessage(),
          encodedMessage,
          headers,
          trailersFrame.trailers());
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

  private Future<Void> handleTrailers(
    GrpcStatus status,
    String statusMessage,
    GrpcMessage message,
    MultiMap headers,
    MultiMap trailers) {
    if (status != GrpcStatus.OK) {
      String msg = statusMessage != null ? statusMessage : status.name();
      eventBusMessage.fail(status.code, msg);
    } else {
      DeliveryOptions options = new DeliveryOptions();
      MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
      if (headers != null) {
        EventBusHeaders.encodeMultiMap(HEADER_PREFIX, headers, multiMap);
      }
      if (trailers != null) {
        EventBusHeaders.encodeMultiMap(TRAILER_PREFIX, trailers, multiMap);
      }
      Buffer payload = message != null ? message.payload() : Buffer.buffer();
      options.setHeaders(multiMap);
      eventBusMessage.reply(wireFormat == WireFormat.JSON ? (payload.length() == 0 ? new JsonObject() : new JsonObject(payload)): payload, options);
    }
    return context.succeededFuture();
  }
}
