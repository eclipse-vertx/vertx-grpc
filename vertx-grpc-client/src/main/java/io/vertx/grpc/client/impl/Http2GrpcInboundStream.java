package io.vertx.grpc.client.impl;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.impl.DefaultGrpcCancelFrame;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcErrorException;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.DefaultGrpcHeadersFrame;
import io.vertx.grpc.common.impl.DefaultGrpcMessageFrame;
import io.vertx.grpc.common.impl.DefaultGrpcTrailersFrame;
import io.vertx.grpc.common.impl.GrpcDeframingStream;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;
import io.vertx.grpc.common.impl.Http2GrpcMessageDeframer;

import java.nio.charset.StandardCharsets;

public class Http2GrpcInboundStream extends Http2GrpcOutboundStream {

  private Handler<GrpcFrame> frameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private GrpcDeframingStream stream;
  private final long maxMessageSize;
  private boolean initialized;

  public Http2GrpcInboundStream(ContextInternal context,
                                HttpClientRequest httpRequest,
                                ServiceName serviceName,
                                String methodName,
                                long maxMessageSize) {
    super(context, httpRequest, serviceName, methodName);
    this.maxMessageSize = maxMessageSize;
  }

  void init() {

    httpRequest.exceptionHandler(err -> {
      handleStreamException(err);
    });

    httpRequest
      .response()
      .onComplete(ar -> {
        if (ar.succeeded()) {
          init(ar.result());
        } else {
          Throwable failure = ar.cause();
          handleStreamException(failure);
        }
      });
  }

  @Override
  protected Future<Void> write(GrpcFrame frame, boolean end) {
    if (!initialized) {
      initialized = true;
      init();
    }
    return super.write(frame, end);
  }

  private void handleStreamException(Throwable failure) {
    if (failure instanceof StreamResetException) {
      GrpcErrorException error = GrpcErrorException.create((StreamResetException) failure);
      if (error.error() == GrpcError.CANCELLED) {
        emit(DefaultGrpcCancelFrame.INSTANCE);
      }
      failure = error;
    }
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(failure);
    }
  }

  private void init(HttpClientResponse httpResponse) {

    String contentType = httpResponse.getHeader(HttpHeaders.CONTENT_TYPE);

    String msg = null;
    String statusHeader = httpResponse.getHeader(GrpcHeaderNames.GRPC_STATUS);
    GrpcStatus status = statusHeader != null ? GrpcStatus.valueOf(Integer.parseInt(statusHeader)) : null;
    WireFormat format = null;
    if (status == null) {
      if (contentType != null) {
        format = GrpcMediaType.parseContentType(contentType, "application/grpc");
      }
      if (contentType == null) {
        msg = "HTTP response missing content-type header";
      } else {
        msg = "Invalid HTTP response content-type header";
      }
    }

    if (format != null || status != null) {

      if (status != null) {
        // Trailers only
        // wait for stream end
        String m = httpResponse.headers().get(GrpcHeaderNames.GRPC_MESSAGE);
        String statusMessage;
        if (m != null) {
          statusMessage = QueryStringDecoder.decodeComponent(m, StandardCharsets.UTF_8);
        } else {
          statusMessage = null;
        }
        GrpcTrailersFrame trailersFrame = new DefaultGrpcTrailersFrame(status, statusMessage, httpResponse.headers());
        emit(trailersFrame);
        httpResponse.endHandler(v -> {
          Handler<Void> handler = endHandler;
          if (handler != null) {
            handler.handle(null);
          }
        });
      } else {

        Http2GrpcMessageDeframer deframer = new Http2GrpcMessageDeframer(httpResponse.headers().get(GrpcHeaderNames.GRPC_ENCODING), format);
        GrpcDeframingStream deframingStream = new GrpcDeframingStream(context,  httpResponse, deframer);
        deframingStream.init(maxMessageSize);
        deframingStream.handler(m -> {
          emit(new DefaultGrpcMessageFrame(m));
        });
        deframingStream.exceptionHandler(err -> {
          Handler<Throwable> handler = exceptionHandler;
          if (handler != null) {
            handler.handle(err);
          }
        });
        deframingStream.endHandler(v -> {
          String responseStatus = httpResponse.getTrailer("grpc-status");
          GrpcStatus status2;
          if (responseStatus != null) {
            status2 = GrpcStatus.valueOf(Integer.parseInt(responseStatus));
          } else {
            status2 = GrpcStatus.UNKNOWN;
          }
          String m = httpResponse.trailers().get(GrpcHeaderNames.GRPC_MESSAGE);
          String statusMessage;
          if (m != null) {
            statusMessage = QueryStringDecoder.decodeComponent(m, StandardCharsets.UTF_8);
          } else {
            statusMessage = null;
          }
          GrpcTrailersFrame trailersFrame = new DefaultGrpcTrailersFrame(status2, statusMessage, httpResponse.trailers());
          emit(trailersFrame);
          Handler<Void> h = endHandler;
          if (h != null) {
            h.handle(null);
          }
        });

        stream = deframingStream;

        String encoding = httpResponse.getHeader(GrpcHeaderNames.GRPC_ENCODING);
        GrpcHeadersFrame headersFrame = new DefaultGrpcHeadersFrame(format, encoding, httpResponse.headers());
        emit(headersFrame);
      }

    } else {
      httpResponse.request().reset(GrpcError.CANCELLED.http2ResetCode);
//      return context().failedFuture(msg);
      throw new UnsupportedOperationException("Handle me: " + msg);
    }
  }

  private void emit(GrpcFrame frame) {
    Handler<GrpcFrame> handler = frameHandler;
    if (handler != null) {
      handler.handle(frame);
    }
  }

  @Override
  public Http2GrpcInboundStream handler(Handler<GrpcFrame> handler) {
    this.frameHandler = handler;
    return this;
  }

  @Override
  public Http2GrpcInboundStream exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public Http2GrpcInboundStream endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public Http2GrpcInboundStream pause() {
    GrpcDeframingStream s = stream;
    if (s != null) {
      s.pause();
    }
    return this;
  }

  @Override
  public Http2GrpcInboundStream resume() {
    GrpcDeframingStream s = stream;
    if (s != null) {
      s.resume();
    }
    return this;
  }

  @Override
  public Http2GrpcInboundStream fetch(long amount) {
    GrpcDeframingStream s = stream;
    if (s != null) {
      s.fetch(amount);
    }
    return this;
  }
}
