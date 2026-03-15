package io.vertx.grpc.server.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.common.GrpcLocal;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.MessageSizeOverflowException;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcInvoker;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcProtocol;

class GrpcDispatcher<Req, Resp> implements Handler<GrpcFrame> {

  private final GrpcInvoker invoker;
  private final ContextInternal context;
  private final GrpcProtocol protocol;
  private final WireFormat format;
  private final GrpcMessageDecoder<Req> messageDecoder;
  private final GrpcMethodCall methodCall;
  private final HttpConnection httpConnection;
  private final GrpcServerImpl.MethodCallHandler<Req, Resp> method;
  private final boolean propagateDeadline;
  private final boolean scheduleDeadline;
  private GrpcServerRequestImpl<Req, Resp> grpcRequest;
  private GrpcServerResponseImpl<Req, Resp> grpcResponse;

  GrpcDispatcher(GrpcInvoker invoker,
                 ContextInternal context,
                 GrpcProtocol protocol,
                 WireFormat format,
                 GrpcMessageDecoder<Req> messageDecoder,
                 GrpcMethodCall methodCall,
                 HttpConnection httpConnection,
                 GrpcServerImpl.MethodCallHandler<Req, Resp> method,
                 boolean propagateDeadline,
                 boolean scheduleDeadline) {
    this.invoker = invoker;
    this.context = context;
    this.protocol = protocol;
    this.format = format;
    this.messageDecoder = messageDecoder;
    this.methodCall = methodCall;
    this.httpConnection = httpConnection;
    this.method = method;
    this.propagateDeadline = propagateDeadline;
    this.scheduleDeadline = scheduleDeadline;
  }

  @Override
  public void handle(GrpcFrame frame) {
    if (frame instanceof GrpcHeadersFrame) {
      GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
      grpcRequest = new GrpcServerRequestImpl<>(
        context,
        headersFrame.headers(),
        protocol,
        format,
        invoker,
        headersFrame.timeout(),
        headersFrame.encoding(),
        messageDecoder,
        methodCall) {
        @Override
        public HttpConnection connection() {
          return httpConnection;
        }
      };
      invoker.endHandler(v -> grpcRequest.handleEnd2());
      grpcResponse = new GrpcServerResponseImpl<>(
        context,
        grpcRequest,
        invoker,
        protocol,
        method.messageEncoder);
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
      grpcRequest.context().dispatch(grpcRequest, method);

    } else if (frame instanceof GrpcMessageFrame) {
      GrpcMessageFrame messageFrame = (GrpcMessageFrame) frame;
      GrpcServerRequestImpl<Req, Resp> r = grpcRequest;
      if (r != null) {
        r.handleMessage(messageFrame.message());
      }
    } else {
      System.out.println("Unhandled frame");
    }
  }

  void handleException(Throwable exception) {
    if (grpcRequest != null) {
      grpcRequest.handleException2(exception);
    }
    if (grpcResponse != null) {
      grpcResponse.handleException(exception);
    }
  }

  void handleEnd() {
    if (grpcRequest != null) {
      grpcRequest.handleEnd2();
    }
  }
}
