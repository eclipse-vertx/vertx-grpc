package io.vertx.grpc.client.impl;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcCancelFrame;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.DefaultGrpcMessage;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcInvoker;
import io.vertx.grpc.common.impl.GrpcMessageFrame;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.grpc.client.impl.GrpcClientRequestImpl.toTimeoutHeader;

public class Http2GrpcClientInvokerResolver implements GrpcClientInvokerResolver {

  private final HttpClientRequest httpRequest;
  private final ContextInternal context;

  public Http2GrpcClientInvokerResolver(HttpClientRequest httpRequest) {
    this.httpRequest = httpRequest;
    this.context = ((PromiseInternal<?>)httpRequest.response()).context();
  }

  @Override
  public GrpcInvoker resolveInvoker(ServiceName serviceName, String methodName) {
    return new GrpcInvoker() {

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

      public Future<Void> write(GrpcFrame frame, boolean end) {

        if (frame instanceof GrpcHeadersFrame) {
          GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;

          MultiMap headers = headersFrame.headers();

          if (headers != null && !headers.isEmpty()) {
            MultiMap requestHeaders = httpRequest.headers();
            for (Map.Entry<String, String> header : headers) {
              requestHeaders.add(header.getKey(), header.getValue());
            }
          }

          Duration timeout = headersFrame.timeout();
          if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            String headerValue = toTimeoutHeader(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (headerValue == null) {
              return context.failedFuture("Not a valid gRPC timeout value (" + timeout + ')');
            }
            httpRequest.putHeader(GrpcHeaderNames.GRPC_TIMEOUT, headerValue);
          }

          String uri = serviceName.pathOf(methodName);
          httpRequest.putHeader(HttpHeaders.CONTENT_TYPE, headersFrame.contentType());
          if (headersFrame.encoding() != null) {
            httpRequest.putHeader(GrpcHeaderNames.GRPC_ENCODING, headersFrame.encoding());
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
        } else if (frame instanceof GrpcMessageFrame) {
          GrpcMessageFrame messageFrame = (GrpcMessageFrame) frame;
          Buffer payload;
          try {
            GrpcMessage message = messageFrame.message();
            payload = DefaultGrpcMessage.encode(message.payload(), message.isCompressed(), false);
          } catch (CodecException e) {
            return context.failedFuture(e);
          }
          if (end) {
            return httpRequest.end(payload);
          } else {
            return httpRequest.write(payload);
          }
        } else if (frame instanceof GrpcCancelFrame) {
          return httpRequest.reset(GrpcError.CANCELLED.http2ResetCode);
        } else if (frame instanceof SetIdleTimeoutFrame) {
          SetIdleTimeoutFrame setIdleTimeout = (SetIdleTimeoutFrame) frame;
          httpRequest.idleTimeout(setIdleTimeout.timeout().toMillis());
          return context.succeededFuture();
        }

        return context.failedFuture("Unsupported");
      }
    };
  }
}
