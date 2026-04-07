package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;

import java.time.Duration;

public class EventBusGrpcStream implements GrpcStream {

  private final ContextInternal context;
  private final EventBus eventBus;
  private final ServiceName serviceName;
  private final String methodName;

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private GrpcMessage message;
  private WireFormat wireFormat;
  private String encoding;
  private MultiMap requestHeaders;
  private Duration timeout;

  public EventBusGrpcStream(ContextInternal context, EventBus eventBus, ServiceName serviceName, String methodName) {
    this.context = context;
    this.eventBus = eventBus;
    this.serviceName = serviceName;
    this.methodName = methodName;
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
  public GrpcInboundStream handler(Handler<GrpcFrame> handler) {
    this.frameHandler = handler;
    return this;
  }

  @Override
  public GrpcInboundStream endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
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
      return context.failedFuture("Should send at least one message");
    }

    Buffer payload = message.payload();
    DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", methodName);

    if (timeout != null) {
      deliveryOptions.setSendTimeout(timeout.toMillis());
    }

    if (requestHeaders != null) {
      for (var entry : requestHeaders) {
        deliveryOptions.addHeader(entry.getKey(), entry.getValue());
      }
    }

    WireFormat resolvedWireFormat = wireFormat != null ? wireFormat : WireFormat.PROTOBUF;
    String resolvedEncoding = encoding != null ? encoding : "identity";
    Future<Message<Buffer>> responseFuture = eventBus.request(serviceName.toString(), payload, deliveryOptions);

    responseFuture.onComplete(ar -> {
      if (ar.succeeded()) {
        Message<Buffer> reply = ar.result();
        emit(new DefaultGrpcHeadersFrame(resolvedWireFormat, resolvedEncoding, MultiMap.caseInsensitiveMultiMap()));
        emit(new DefaultGrpcMessageFrame(GrpcMessage.message(resolvedEncoding, resolvedWireFormat, reply.body())));
        emit(new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, MultiMap.caseInsensitiveMultiMap()));
      } else {
        GrpcStatus status = mapFailure(ar.cause());
        emit(new DefaultGrpcHeadersFrame(resolvedWireFormat, resolvedEncoding, MultiMap.caseInsensitiveMultiMap()));
        emit(new DefaultGrpcTrailersFrame(status, ar.cause().getMessage(), MultiMap.caseInsensitiveMultiMap()));
      }

      Handler<Void> handler = endHandler;
      if (handler != null) {
        handler.handle(null);
      }
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

      // RECIPIENT_FAILURE — use the failure code if it maps to a valid gRPC status
      GrpcStatus status = GrpcStatus.valueOf(replyException.failureCode());
      if (status != null) {
        return status;
      }
    }
    return GrpcStatus.INTERNAL;
  }

  private void emit(GrpcFrame frame) {
    Handler<GrpcFrame> handler = frameHandler;
    if (handler != null) {
      handler.handle(frame);
    }
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
