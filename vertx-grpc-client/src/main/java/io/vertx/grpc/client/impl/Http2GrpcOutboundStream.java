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
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.grpc.client.impl.GrpcClientRequestImpl.toTimeoutHeader;

abstract class Http2GrpcOutboundStream implements GrpcStream {

  protected final ContextInternal context;
  protected final HttpClientRequest httpRequest;
  protected final ServiceName serviceName;
  protected final String methodName;
  private Future<Void> halfCloseSent;

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
  public Future<Void> fail(GrpcError error) {
    return httpRequest.reset(error.http2ResetCode);
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    if (frame.type() == GrpcFrameType.HALF_CLOSE) {
      return doWrite(frame);
    } else {
      return context.failedFuture("Stream must be ended with an half-close frame");
    }
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    return doWrite(frame);
  }

  @Override
  public Future<Void> end() {
    Future<Void> ret = halfCloseSent;
    if (ret == null) {
      return context.failedFuture("Stream must be ended with an half-close frame prior ending it");
    }
    return ret;
  }

  protected Future<Void> doWrite(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        if (halfCloseSent != null) {
          return context.failedFuture("Trailers message sent");
        }
        return handleHeadersFrame((GrpcHeadersFrame) frame);
      case MESSAGE:
        if (halfCloseSent != null) {
          return context.failedFuture("Trailers message sent");
        }
        return handleMessageFrame((GrpcMessageFrame) frame);
      case HALF_CLOSE:
        if (halfCloseSent != null) {
          return context.failedFuture("Trailers message sent");
        }
        return halfCloseSent = httpRequest.end();
      case OTHER:
        if (frame instanceof SetIdleTimeoutFrame) {
          return handleSetIdleTimeout((SetIdleTimeoutFrame) frame);
        }
        // Fall through
      default:
        return context.failedFuture("Unsupported frame " + frame.type());
    }
  }

  private Future<Void> handleHeadersFrame(GrpcHeadersFrame frame) {
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

    String contentType = frame.format().mediaType();

    String uri = serviceName.pathOf(methodName);
    httpRequest.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
    if (frame.encoding() != null) {
      httpRequest.putHeader(GrpcHeaderNames.GRPC_ENCODING, frame.encoding());
    }
    httpRequest.putHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING, "gzip");
    httpRequest.putHeader(HttpHeaderNames.TE, "trailers");
    httpRequest.setChunked(true);
    httpRequest.setURI(uri);

    return httpRequest.writeHead();
  }

  private Future<Void> handleMessageFrame(GrpcMessageFrame frame) {
    Buffer payload;
    try {
      GrpcMessage message = frame.message();
      payload = DefaultGrpcMessage.encode(message.payload(), message.isCompressed(), false);
    } catch (CodecException e) {
      return context.failedFuture(e);
    }
    return httpRequest.write(payload);
  }

  private Future<Void> handleSetIdleTimeout(SetIdleTimeoutFrame frame) {
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
