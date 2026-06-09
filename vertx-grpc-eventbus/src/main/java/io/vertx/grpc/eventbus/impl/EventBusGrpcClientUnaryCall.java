package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
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

public class EventBusGrpcClientUnaryCall implements GrpcStream {

  private final ContextInternal context;
  private final EventBus eventBus;
  private final ServiceName serviceName;
  private final String methodName;

  private WireFormat wireFormat = WireFormat.PROTOBUF;
  private String encoding = "identity";
  private MultiMap requestHeaders;
  private Duration timeout;
  private boolean sent;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;

  public EventBusGrpcClientUnaryCall(ContextInternal context, EventBus eventBus, ServiceName serviceName, String methodName) {
    this.context = context;
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
        return send(((GrpcMessageFrame) frame).message());
      default:
        return context.succeededFuture();
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    if (!sent) {
      return send(null);
    }
    return context.succeededFuture();
  }

  private Future<Void> send(GrpcMessage message) {
    if (sent) {
      return context.succeededFuture();
    }
    sent = true;

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

    eventBus.request(serviceName.fullyQualifiedName(), body, options).onComplete(ar -> {
      if (ar.succeeded()) {
        handleReply(ar.result());
      } else {
        handleFailure(ar.cause());
      }
    });

    return context.succeededFuture();
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

  private void emit(GrpcFrame frame) {
    Handler<GrpcFrame> handler = frameHandler;
    if (handler != null) {
      handler.handle(frame);
    }
  }

  private void emitEnd() {
    Handler<Void> handler = endHandler;
    if (handler != null) {
      handler.handle(null);
    }
  }

  @Override
  public GrpcStream handler(Handler<GrpcFrame> handler) {
    this.frameHandler = handler;
    return this;
  }

  @Override
  public GrpcStream endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public GrpcStream exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public GrpcInboundStream pause() {
    return this;
  }

  @Override
  public GrpcInboundStream resume() {
    return this;
  }

  @Override
  public GrpcInboundStream fetch(long amount) {
    return this;
  }

  @Override
  public GrpcOutboundStream setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return false;
  }

  @Override
  public GrpcOutboundStream drainHandler(Handler<Void> handler) {
    return this;
  }
}
