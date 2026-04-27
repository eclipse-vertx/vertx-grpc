package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.eventbus.EventBusHeaders;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;

import java.time.Duration;
import java.util.Optional;

public class EventBusGrpcClientStream extends EventBusGrpcStreamBase {

  private final EventBus eventBus;
  private final ServiceName serviceName;
  private final String methodName;

  private GrpcMessage message;
  private WireFormat wireFormat;
  private String encoding;
  private MultiMap requestHeaders;
  private Duration timeout;

  public EventBusGrpcClientStream(ContextInternal context, EventBus eventBus, ServiceName serviceName, String methodName) {
    super(context);
    this.eventBus = eventBus;
    this.serviceName = serviceName;
    this.methodName = methodName;
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
        wireFormat = headersFrame.format();
        encoding = headersFrame.encoding();
        requestHeaders = headersFrame.headers();
        timeout = headersFrame.timeout();
        return context.succeededFuture();
      case MESSAGE:
        if (message != null) {
          // TODO: support streaming this depends on the event bus transport supporting streaming first
          // See https://github.com/eclipse-vertx/vert.x/pull/4712
          return context.failedFuture(new UnsupportedOperationException("Streaming is not supported over event bus transport"));
        }

        GrpcMessageFrame messageFrame = (GrpcMessageFrame) frame;
        message = messageFrame.message();
        return context.succeededFuture();
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> end() {
    if (message == null) {
      return context.failedFuture(new IllegalStateException("No message received"));
    }

    Buffer payload = message.payload();
    DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader(EventBusHeaders.ACTION, methodName);

    if (timeout != null) {
      deliveryOptions.setSendTimeout(timeout.toMillis());
    }

    if (requestHeaders != null) {
      for (var entry : requestHeaders) {
        deliveryOptions.addHeader(entry.getKey(), entry.getValue());
      }
    }

    String encoding = Optional.ofNullable(this.encoding).orElse("identity");
    WireFormat wireFormat = Optional.ofNullable(this.wireFormat).orElse(WireFormat.PROTOBUF);

    deliveryOptions.addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name());

    Object body = wireFormat == WireFormat.JSON ? (payload.length() == 0 ? new JsonObject() : new JsonObject(payload)) : payload;
    Future<Message<Object>> response = eventBus.request(serviceName.fullyQualifiedName(), body, deliveryOptions);

    response.onComplete(ar -> {
      if (ar.succeeded()) {
        Message<Object> reply = ar.result();
        Buffer replyPayload = EventBusGrpcBody.asBuffer(reply.body());
        emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, MultiMap.caseInsensitiveMultiMap()));
        emit(new DefaultGrpcMessageFrame(GrpcMessage.message(encoding, wireFormat, replyPayload)));
        emit(new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, MultiMap.caseInsensitiveMultiMap()));
      } else {
        GrpcStatus status = mapFailure(ar.cause());
        emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, MultiMap.caseInsensitiveMultiMap()));
        emit(new DefaultGrpcTrailersFrame(status, ar.cause().getMessage(), MultiMap.caseInsensitiveMultiMap()));
      }

      emitEnd();
    });

    return context.succeededFuture();
  }

  private static GrpcStatus mapFailure(Throwable cause) {
    if (cause instanceof ReplyException) {
      ReplyException replyException = (ReplyException) cause;
      if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
        return GrpcStatus.UNAVAILABLE;
      }

      if (replyException.failureType() == ReplyFailure.TIMEOUT) {
        return GrpcStatus.DEADLINE_EXCEEDED;
      }

      // RECIPIENT_FAILURE - use the failure code if it maps to a valid gRPC status
      GrpcStatus status = GrpcStatus.valueOf(replyException.failureCode());
      if (status != null) {
        return status;
      }
    }
    return GrpcStatus.INTERNAL;
  }
}
