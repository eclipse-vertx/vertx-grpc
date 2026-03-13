package io.vertx.grpc.server.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.impl.DefaultGrpcMessage;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcTrailersFrame;
import io.vertx.grpc.common.impl.Utils;

import java.util.Map;

// Stateless protocol handler
public abstract class HttpGrpcServerInvoker implements GrpcServerInvoker {

  private final ContextInternal contextInternal;
  private final HttpServerRequest httpRequest;
  private final HttpServerResponse httpResponse;
  protected GrpcStatus status;

  public HttpGrpcServerInvoker(HttpServerRequest httpRequest) {
    this.contextInternal = ((HttpServerRequestInternal) httpRequest).context();
    this.httpRequest = httpRequest;
    this.httpResponse = httpRequest.response();
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    if (frame instanceof GrpcHeadersFrame) {
      if (frame instanceof GrpcTrailersFrame) {
        GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
        GrpcTrailersFrame trailersFrame = (GrpcTrailersFrame) frame;
        return writeTrailers(headersFrame.contentType(), headersFrame.encoding(), trailersFrame.status(), trailersFrame.statusMessage(), headersFrame.headers(), trailersFrame.trailers());
      } else {
        return writeHeaders((GrpcHeadersFrame) frame);
      }
    } else if (frame instanceof GrpcMessageFrame) {
      return writeMessage((GrpcMessageFrame) frame);
    } else if (frame instanceof GrpcTrailersFrame) {
      return writeTrailers((GrpcTrailersFrame) frame);
    }
    return contextInternal.failedFuture("Invalid message");
  }

  protected Future<Void> writeHeaders(GrpcHeadersFrame frame) {
    MultiMap httpHeaders = httpResponse.headers();
    httpHeaders.set("content-type", frame.contentType());
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

  protected Future<Void> writeTrailers(String contentType, String encoding, GrpcStatus st, String statusMessage, MultiMap headers, MultiMap trailers) {
    status = st;
    boolean trailersOnly = st != GrpcStatus.OK;
    MultiMap httpHeaders = httpResponse.headers();
    httpHeaders.set("content-type", contentType);
    encodeGrpcHeaders(headers, httpHeaders, encoding);
    writeTrailers(trailersOnly, trailers, st, statusMessage);
    return writeEnd();
  }

  protected Future<Void> writeTrailers(GrpcTrailersFrame frame) {
    status = frame.status();
    writeTrailers(false, frame.trailers(), frame.status(), frame.statusMessage());
    return writeEnd();
  }

  protected void writeTrailers(boolean trailersOnly, MultiMap grpcTrailers, GrpcStatus st, String statusMessage) {
    MultiMap httpTrailers;
    if (trailersOnly) {
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
    return httpResponse.end();
  }

  protected Future<Void> writeMessage(GrpcMessageFrame frame) {
    Buffer payload;
    try {
      payload = frame.message().payload();
    } catch (CodecException e) {
      return contextInternal.failedFuture(e);
    }
    return httpResponse.write(encodeMessage(payload, frame.message().isCompressed(), false));
  }

  protected Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    return DefaultGrpcMessage.encode(message, compressed, trailer);
  }
}
