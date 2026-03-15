package io.vertx.grpc.server.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.impl.DefaultGrpcHeadersFrame;
import io.vertx.grpc.common.impl.DefaultGrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcDeframingStream;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcInboundInvoker;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;

public class HttpGrpcInboundInvoker implements GrpcInboundInvoker {

  private final ContextInternal context;
  private final GrpcMessageDeframer deframer;
  private GrpcDeframingStream deframingStream;
  private Handler<GrpcFrame> frameHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;

  public HttpGrpcInboundInvoker(ContextInternal context, GrpcMessageDeframer deframer) {
    this.context = context;
    this.deframer = deframer;
  }

  @Override
  public GrpcInboundInvoker handler(Handler<GrpcFrame> handler) {
    this.frameHandler = handler;
    return this;
  }

  @Override
  public GrpcInboundInvoker exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public GrpcInboundInvoker endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  void init(HttpServerRequest httpRequest, long maxMessageSize) {

    // Wire
    GrpcDeframingStream stream = new GrpcDeframingStream(context, httpRequest, deframer);
    stream.handler(message -> {
      emit(new DefaultGrpcMessageFrame(message));
    });

    stream.exceptionHandler(err -> {
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        handler.handle(err);
      }
    });

    stream.endHandler(v -> {
      Handler<Void> handler = endHandler;
      if (handler != null) {
        handler.handle(null);
      }
    });

    stream.init(maxMessageSize);

    deframingStream = stream;

    // Fire GrpcHeadersFrame event
    String encoding = httpRequest.headers().get(GrpcHeaderNames.GRPC_ENCODING);
    String contentType = httpRequest.headers().get(HttpHeaders.CONTENT_TYPE);
    GrpcHeadersFrame headersFrame = new DefaultGrpcHeadersFrame(contentType, encoding, httpRequest.headers());

    emit(headersFrame);
  }

  private void emit(GrpcFrame frame) {
    Handler<GrpcFrame> handler = frameHandler;
    if (handler != null) {
      handler.handle(frame);
    }
  }

  @Override
  public GrpcInboundInvoker pause() {
    deframingStream.pause();
    return this;
  }

  @Override
  public GrpcInboundInvoker resume() {
    deframingStream.resume();
    return this;
  }

  @Override
  public GrpcInboundInvoker fetch(long amount) {
    deframingStream.fetch(amount);
    return this;
  }
}
