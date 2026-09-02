package io.vertx.grpc.server.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.server.GrpcServerRequest;

public class GrpcDispatcher<Req, Resp> implements Handler<GrpcFrame> {

  private final GrpcMethodCall<Req, Resp> methodCall;
  private final ContextInternal context;
  private final HttpConnection httpConnection;
  private final Handler<GrpcServerRequest<Req, Resp>> handler;
  private final boolean propagateDeadline;
  private final boolean scheduleDeadline;
  private GrpcServerRequestImpl<Req, Resp> grpcRequest;
  private GrpcServerResponseImpl<Req, Resp> grpcResponse;

  public GrpcDispatcher(ContextInternal context,
                        GrpcMethodCall<Req, Resp> methodCall,
                        HttpConnection httpConnection,
                        Handler<GrpcServerRequest<Req, Resp>> handler,
                        boolean propagateDeadline,
                        boolean scheduleDeadline) {
    this.methodCall = methodCall;
    this.context = context;
    this.httpConnection = httpConnection;
    this.handler = handler;
    this.propagateDeadline = propagateDeadline;
    this.scheduleDeadline = scheduleDeadline;
  }

  @Override
  public void handle(GrpcFrame frame) {

    switch (frame.type()) {
      case HEADERS:
        handleHeadersFrame((GrpcHeadersFrame) frame);
        break;
      case MESSAGE:
        handleMessage((GrpcMessageFrame) frame);
        break;
      case HALF_CLOSE:
        handleHalfClose();
        break;
      default:
        // Log
        break;
    }
  }

  private void handleHeadersFrame(GrpcHeadersFrame frame) {
    WireFormat format = frame.format();
    grpcRequest = new GrpcServerRequestImpl<>(
      context,
      frame.metadata(),
      format,
      methodCall.stream(),
      frame.timeout(),
      frame.encoding(),
      methodCall.messageDecoder(),
      methodCall.serviceName(),
      methodCall.fullMethodName(),
      methodCall.methodName()) {
      @Override
      public HttpConnection connection() {
        return httpConnection;
      }
    };
    grpcResponse = new GrpcServerResponseImpl<>(
      context,
      grpcRequest,
      methodCall.stream(),
      methodCall.messageEncoder());
    grpcResponse.format(format);
    long timeout = grpcRequest.timeout();
    if (propagateDeadline && timeout > 0L) {
      long deadline = System.currentTimeMillis() + timeout;
      grpcRequest.context().putLocal(GrpcLocal.CONTEXT_LOCAL_KEY, AccessMode.CONCURRENT, new GrpcLocal(deadline));
    }
    grpcRequest.init(grpcResponse, scheduleDeadline);
    grpcRequest.invalidMessageHandler(invalidMsg -> {
      if (invalidMsg instanceof MessageSizeOverflowException) {
        grpcRequest.response().status(GrpcStatus.RESOURCE_EXHAUSTED).end();
      } else {
        grpcResponse.cancel();
      }
    });
    grpcRequest.context().dispatch(grpcRequest, req -> {
      try {
        handler.handle(req);
      } catch (Exception e) {
        handleInvocationFailure(e);
      }
    });
  }

  private void handleInvocationFailure(Exception e) {
    if (grpcResponse.isCancelled() || grpcResponse.isEndWritten()) {
      context.reportException(e);
    } else {
      grpcResponse.fail(e);
    }
  }

  private void handleMessage(GrpcMessageFrame frame) {
    GrpcServerRequestImpl<Req, Resp> r = grpcRequest;
    if (r != null) {
      r.handleMessage(frame.message());
    }
  }

  private void handleHalfClose() {
    if (grpcRequest != null) {
      grpcRequest.handleEnd();
    }
  }

  public void handleException(Throwable exception) {
    if (grpcRequest != null) {
      grpcRequest.handleException(exception);
    }
  }

  public void handleError(GrpcError error) {
    if (grpcRequest != null) {
      grpcRequest.handleError(error);
    }
  }

  public void handleEnd(Void v) {
  }
}
