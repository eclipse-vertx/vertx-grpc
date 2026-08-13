package io.vertx.grpc.server.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServerRequest;

public class GrpcDispatcher<Req, Resp> implements Handler<GrpcFrame> {

  private final GrpcStream stream;
  private final ContextInternal context;
  private final GrpcProtocol protocol;
  private final WireFormat format;
  private final GrpcMessageDecoder<Req> messageDecoder;
  private final GrpcMessageEncoder<Resp> messageEncoder;
  private final GrpcMethodCall methodCall;
  private final HttpConnection httpConnection;
  private final Handler<GrpcServerRequest<Req, Resp>> method;
  private final boolean propagateDeadline;
  private final boolean scheduleDeadline;
  private GrpcServerRequestImpl<Req, Resp> grpcRequest;
  private GrpcServerResponseImpl<Req, Resp> grpcResponse;

  public GrpcDispatcher(GrpcStream stream,
                        ContextInternal context,
                        GrpcProtocol protocol,
                        WireFormat format,
                        GrpcMessageDecoder<Req> messageDecoder,
                        GrpcMessageEncoder<Resp> messageEncoder,
                        GrpcMethodCall methodCall,
                        HttpConnection httpConnection,
                        Handler<GrpcServerRequest<Req, Resp>> method,
                        boolean propagateDeadline,
                        boolean scheduleDeadline) {
    this.stream = stream;
    this.context = context;
    this.protocol = protocol;
    this.format = format;
    this.messageDecoder = messageDecoder;
    this.messageEncoder = messageEncoder;
    this.methodCall = methodCall;
    this.httpConnection = httpConnection;
    this.method = method;
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
      case CANCEL:
        handleCancel((GrpcCancelFrame) frame);
        break;
      default:
        // Log
        break;
    }
  }

  private void handleHeadersFrame(GrpcHeadersFrame frame) {
    grpcRequest = new GrpcServerRequestImpl<>(
      context,
      frame.headers(),
      protocol,
      format,
      stream,
      frame.timeout(),
      frame.encoding(),
      messageDecoder,
      methodCall) {
      @Override
      public HttpConnection connection() {
        return httpConnection;
      }
    };
    stream.endHandler(v -> grpcRequest.handleEnd());
    grpcResponse = new GrpcServerResponseImpl<>(
      context,
      grpcRequest,
      stream,
      protocol,
      messageEncoder);
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
        method.handle(req);
      } catch (Exception e) {
        handleInvocationFailure(e);
      }
    });
  }

  private void handleInvocationFailure(Exception e) {
    if (grpcResponse.isCancelled() || grpcResponse.isTrailersSent()) {
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

  private void handleCancel(GrpcCancelFrame frame) {
    GrpcServerRequestImpl<Req, Resp> r = grpcRequest;
    if (r != null) {
      r.handleCancel();
    }
  }

  public void handleException(Throwable exception) {
    if (grpcRequest != null) {
      grpcRequest.handleException(exception);
    }
  }

  public void handleEnd() {
    if (grpcRequest != null) {
      grpcRequest.handleEnd();
    }
  }
}
