package io.vertx.grpc.client.impl;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcCancelFrame;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.ProtobufWireFormat;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.DefaultGrpcMessage;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.common.impl.GrpcMessageFrame;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.grpc.client.impl.GrpcClientRequestImpl.toTimeoutHeader;

abstract class Http2GrpcOutboundStream implements GrpcStream {

  protected final ContextInternal context;
  protected final HttpClientRequest httpRequest;
  protected final ServiceName serviceName;
  protected final String methodName;

  public Http2GrpcOutboundStream(ContextInternal context,
                                 HttpClientRequest httpRequest,
                                 ServiceName serviceName,
                                 String methodName) {
    this.context = context;
    this.httpRequest = httpRequest;
    this.serviceName = serviceName;
    this.methodName = methodName;
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame, true);
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    return write(frame, false);
  }

  @Override
  public Future<Void> end() {
    return httpRequest.end();
  }

  protected Future<Void> write(GrpcFrame frame, boolean end) {
    switch (frame.type()) {
      case HEADERS:
        return handleHeadersFrame((GrpcHeadersFrame) frame, end);
      case MESSAGE:
        return handleMessageFrame((GrpcMessageFrame) frame, end);
      case CANCEL:
        return handleCancelFrame((GrpcCancelFrame) frame, end);
      case OTHER:
        if (frame instanceof SetIdleTimeoutFrame) {
          return handleSetIdleTimeout((SetIdleTimeoutFrame) frame, end);
        }
        // Fall through
      default:
        return context.failedFuture("Unsupported frame " + frame.type());
    }
  }

  private Future<Void> handleHeadersFrame(GrpcHeadersFrame frame, boolean end) {
    MultiMap headers = frame.headers();

    if (headers != null && !headers.isEmpty()) {
      MultiMap requestHeaders = httpRequest.headers();
      for (Map.Entry<String, String> header : headers) {
        requestHeaders.add(header.getKey(), header.getValue());
      }
    }

    Duration timeout = frame.timeout();
    if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
      String headerValue = toTimeoutHeader(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (headerValue == null) {
        return context.failedFuture("Not a valid gRPC timeout value (" + timeout + ')');
      }
      httpRequest.putHeader(GrpcHeaderNames.GRPC_TIMEOUT, headerValue);
    }

    String contentType;
    WireFormat format = frame.format();
    if (format instanceof ProtobufWireFormat) {
      contentType = "application/grpc";
    } else if (format instanceof JsonWireFormat) {
      contentType = "application/grpc+json";
    } else {
      throw new UnsupportedOperationException();
    }

    String uri = serviceName.pathOf(methodName);
    httpRequest.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
    if (frame.encoding() != null) {
      httpRequest.putHeader(GrpcHeaderNames.GRPC_ENCODING, frame.encoding());
    }
    httpRequest.putHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING, "gzip");
    httpRequest.putHeader(HttpHeaderNames.TE, "trailers");
    httpRequest.setChunked(true);
    httpRequest.setURI(uri);

    if (end) {
      return httpRequest.end();
    } else {
      return httpRequest.writeHead();
    }
  }

  private Future<Void> handleMessageFrame(GrpcMessageFrame frame, boolean end) {
    Buffer payload;
    try {
      GrpcMessage message = frame.message();
      payload = DefaultGrpcMessage.encode(message.payload(), message.isCompressed(), false);
    } catch (CodecException e) {
      return context.failedFuture(e);
    }
    if (end) {
      return httpRequest.end(payload);
    } else {
      return httpRequest.write(payload);
    }
  }

  private Future<Void> handleCancelFrame(GrpcCancelFrame frame, boolean end) {
    return httpRequest.reset(GrpcError.CANCELLED.http2ResetCode);
  }

  private Future<Void> handleSetIdleTimeout(SetIdleTimeoutFrame frame, boolean end) {
    httpRequest.idleTimeout(frame.timeout().toMillis());
    return context.succeededFuture();
  }

  @Override
  public Http2GrpcOutboundStream exceptionHandler(@Nullable Handler<Throwable> handler) {
    return this;
  }

  @Override
  public Http2GrpcOutboundStream setWriteQueueMaxSize(int maxSize) {
    httpRequest.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return httpRequest.writeQueueFull();
  }

  @Override
  public Http2GrpcOutboundStream drainHandler(@Nullable Handler<Void> handler) {
    httpRequest.drainHandler(handler);
    return this;
  }
}
