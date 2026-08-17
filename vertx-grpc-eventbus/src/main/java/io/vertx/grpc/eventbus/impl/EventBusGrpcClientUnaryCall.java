package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;

import java.time.Duration;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

public class EventBusGrpcClientUnaryCall extends EventBusGrpcCallBase {

  private final EventBus eventBus;
  private final ServiceName serviceName;
  private final String methodName;

  private WireFormat wireFormat = WireFormat.PROTOBUF;
  private String encoding = "identity";
  private MultiMap requestHeaders;
  private Duration timeout;
  private boolean ended;
  private GrpcMessage message;

  public EventBusGrpcClientUnaryCall(ContextInternal context, EventBus eventBus, ServiceName serviceName, String methodName) {
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
        if (headersFrame.format() != null) {
          wireFormat = headersFrame.format();
        }
        if (headersFrame.encoding() != null) {
          encoding = headersFrame.encoding();
        }
        requestHeaders = headersFrame.headers();
        timeout = headersFrame.timeout();
        return context.succeededFuture();
      case MESSAGE:
        message = ((GrpcMessageFrame) frame).message();
        return context.succeededFuture();
      default:
        return context.failedFuture("Frame not handled");
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    if (ended) {
      return context.failedFuture("Already sent");
    }
    GrpcMessage msg = message;
    if (msg == null) {
      return context.failedFuture("No message to send");
    }
    ended = true;
    message = null;
    return send(msg);
  }

  private Future<Void> send(GrpcMessage message) {

    DeliveryOptions options = new DeliveryOptions()
      .addHeader(EventBusHeaders.ACTION, methodName)
      .addHeader(EventBusHeaders.WIRE_FORMAT, wireFormat.name());

    if (timeout != null) {
      options.setSendTimeout(timeout.toMillis());
    }

    if (requestHeaders != null) {
      EventBusHeaders.encodeMultiMap(HEADER_PREFIX, requestHeaders, options.getHeaders());
    }

    Buffer payload = message != null ? message.payload() : Buffer.buffer();
    Object body = EventBusGrpcCodec.encodeBody(payload, wireFormat);

    Promise<Void> promise = context.promise();

    eventBus.request(serviceName.fullyQualifiedName(), body, options).onComplete(ar -> {
      if (ar.succeeded()) {
        handleReply(ar.result());
        promise.succeed();
      } else {
        handleFailure(ar.cause());
        promise.fail(ar.cause());
      }
    });

    return promise.future();
  }

  private void handleReply(Message<Object> reply) {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    MultiMap trailers = MultiMap.caseInsensitiveMultiMap();
    EventBusHeaders.decodeMultimap(HEADER_PREFIX, reply.headers(), headers);
    EventBusHeaders.decodeMultimap(TRAILER_PREFIX, reply.headers(), trailers);
    Buffer payload = EventBusGrpcCodec.decodeBody(reply.body());
    emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, headers));
    emit(new DefaultGrpcMessageFrame(GrpcMessage.message(encoding, wireFormat, payload)));
    emit(new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, trailers));
    emitEnd();
  }

  private void handleFailure(Throwable cause) {
    GrpcStatus status = EventBusGrpcCodec.mapFailure(cause);
    emit(new DefaultGrpcHeadersFrame(wireFormat, encoding, MultiMap.caseInsensitiveMultiMap()));
    emit(new DefaultGrpcTrailersFrame(status, cause.getMessage(), MultiMap.caseInsensitiveMultiMap()));
    emitEnd();
  }

}
