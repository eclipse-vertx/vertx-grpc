package io.vertx.grpc.server.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.DefaultGrpcMessage;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcOutboundStream;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;
import io.vertx.grpc.common.impl.Utils;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Map;

public abstract class HttpGrpcOutboundStream extends HttpGrpcInboundStream implements GrpcStream {

  private boolean headersSent;
  private final HttpServerResponse httpResponse;
  protected GrpcStatus status;

  public HttpGrpcOutboundStream(HttpServerRequest httpRequest, GrpcProtocol protocol, GrpcMessageDeframer deframer) {
    super(((HttpServerRequestInternal) httpRequest).context(), protocol, deframer);
    this.httpResponse = httpRequest.response();
  }

  protected abstract String contentType(WireFormat wireFormat);

  void init() {
    httpResponse.exceptionHandler(this::handleException);
  }

  @Override
  public Future<Void> end() {
    return context.succeededFuture();
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    return write(frame);
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    switch (frame.type()) {
      case HEADERS:
        return writeHeaders((GrpcHeadersFrame) frame);
      case MESSAGE:
        return writeMessage((GrpcMessageFrame) frame);
      case TRAILERS:
        return writeTrailers((GrpcTrailersFrame) frame);
      default:
        return context.failedFuture("Invalid message: " + frame.type());
    }
  }

  protected Future<Void> writeHeaders(GrpcHeadersFrame frame) {
    headersSent = true;
    MultiMap httpHeaders = httpResponse.headers();
    String contentType = contentType(frame.format());
    httpHeaders.set("content-type", contentType);
    encodeGrpcHeaders(frame.headers(), httpHeaders, frame.encoding());
    return writeHead();
  }

  protected Future<Void> writeHead() {
    return httpResponse.writeHead();
  }

  protected void encodeGrpcHeaders(MultiMap grpcHeaders, MultiMap httpHeaders, String encoding) {
    if (grpcHeaders != null && !grpcHeaders.isEmpty()) {
      for (Map.Entry<String, String> header : grpcHeaders) {
        httpHeaders.add(header.getKey(), header.getValue());
      }
    }
  }

  /**
   * Encode grpc status and status message in the specified {@code entries} map.
   *
   * @param entries the map updated with grpc specific headers
   */
  protected final void encodeGrpcStatus(MultiMap entries, GrpcStatus status, String statusMessage) {
    if (!entries.contains(GrpcHeaderNames.GRPC_STATUS)) {
      entries.set(GrpcHeaderNames.GRPC_STATUS, status.toString());
    }
    if (status != GrpcStatus.OK) {
      if (statusMessage != null && !entries.contains(GrpcHeaderNames.GRPC_MESSAGE)) {
        entries.set(GrpcHeaderNames.GRPC_MESSAGE, Utils.utf8PercentEncode(statusMessage));
      }
    } else {
      entries.remove(GrpcHeaderNames.GRPC_MESSAGE);
    }
  }

  protected Future<Void> writeTrailers(GrpcTrailersFrame frame) {
    GrpcStatus st = frame.status();
    boolean trailersOnly = st != GrpcStatus.OK;
    if (!headersSent) {
      httpResponse.putHeader(HttpHeaders.CONTENT_TYPE, protocol.mediaType());
      if (!trailersOnly) {
        // Service has sent no messages
        headersSent = true;
        httpResponse.writeHead();
      }
    }
    status = st;
    writeTrailers(!headersSent, frame.trailers(), st, frame.statusMessage());
    return writeEnd();
  }

  protected void writeTrailers(boolean useHeaders, MultiMap grpcTrailers, GrpcStatus st, String statusMessage) {
    MultiMap httpTrailers;
    if (useHeaders) {
      httpTrailers = httpResponse.headers();
    } else {
      httpTrailers = httpResponse.trailers();
    }
    encodeGrpcTrailers(grpcTrailers, httpTrailers);
    encodeGrpcStatus(httpTrailers, st, statusMessage);
  }

  protected final void encodeGrpcTrailers(MultiMap grpcTrailers, MultiMap httpTrailers) {
    if (grpcTrailers != null && !grpcTrailers.isEmpty()) {
      for (Map.Entry<String, String> header : grpcTrailers) {
        httpTrailers.add(header.getKey(), header.getValue());
      }
    }
  }

  protected Future<Void> writeEnd() {
    headersSent = true;
    return httpResponse.end();
  }

  protected Future<Void> writeMessage(GrpcMessageFrame frame) {
    Buffer payload;
    try {
      payload = frame.message().payload();
    } catch (CodecException e) {
      return context.failedFuture(e);
    }
    headersSent = true;
    return httpResponse.write(encodeMessage(payload, frame.message().isCompressed(), false));
  }

  protected Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    return DefaultGrpcMessage.encode(message, compressed, trailer);
  }

  @Override
  public HttpGrpcOutboundStream exceptionHandler(@Nullable Handler<Throwable> handler) {
    super.exceptionHandler(handler);
    return this;
  }

  @Override
  public GrpcOutboundStream setWriteQueueMaxSize(int maxSize) {
    httpResponse.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return httpResponse.writeQueueFull();
  }

  @Override
  public GrpcOutboundStream drainHandler(@Nullable Handler<Void> handler) {
    httpResponse.drainHandler(handler);
    return this;
  }
}
