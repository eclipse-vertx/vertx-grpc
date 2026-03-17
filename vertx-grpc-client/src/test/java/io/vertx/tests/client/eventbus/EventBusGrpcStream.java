package io.vertx.tests.client.eventbus;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.DefaultGrpcHeadersFrame;
import io.vertx.grpc.common.impl.DefaultGrpcMessageFrame;
import io.vertx.grpc.common.impl.DefaultGrpcTrailersFrame;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcInboundStream;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcOutboundStream;
import io.vertx.grpc.common.impl.GrpcStream;

public class EventBusGrpcStream implements GrpcStream {

  private final ContextInternal context;
  private final ServiceName serviceName;
  private final String methodName;
  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;

  public EventBusGrpcStream(ContextInternal context, ServiceName serviceName, String methodName) {
    this.context = context;
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

  private GrpcMessage message;

  @Override
  public Future<Void> write(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
        return context.succeededFuture();
      case MESSAGE:
        if (message != null) {
          return context.failedFuture("Streaming not supported");
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
    JsonObject json = message.payload().toJsonObject();
    EventBus eventBus = context
      .owner()
      .eventBus();
    Future<Message<JsonObject>> f = eventBus
      .request(serviceName.toString(), json, new DeliveryOptions().addHeader("action", methodName));
    f.onComplete(ar -> {
      if (ar.succeeded()) {
        Message<JsonObject> reply = ar.result();
        emit(new DefaultGrpcHeadersFrame(WireFormat.JSON, "identity", MultiMap.caseInsensitiveMultiMap()));
        emit(new DefaultGrpcMessageFrame(GrpcMessage.message("identity", WireFormat.JSON, reply.body().toBuffer())));
        emit(new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, MultiMap.caseInsensitiveMultiMap()));
        Handler<Void> handler = endHandler;
        if (handler != null) {
          handler.handle(null);
        }
      } else {

      }
    });
    return context.succeededFuture();
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
    throw new UnsupportedOperationException();
  }

  @Override
  public GrpcOutboundStream drainHandler(Handler<Void> handler) {
    return this;
  }
}
