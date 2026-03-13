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
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.impl.GrpcMessageImpl;
import io.vertx.grpc.common.impl.Utils;

import java.util.Map;

// Stateless protocol handler
public abstract class HttpGrpcServerInvoker implements GrpcServerInvoker {

  private final ContextInternal contextInternal;
  private final HttpServerRequest httpRequest;
  private final HttpServerResponse httpResponse;

  public HttpGrpcServerInvoker(HttpServerRequest httpRequest) {
    this.contextInternal = ((HttpServerRequestInternal) httpRequest).context();
    this.httpRequest = httpRequest;
    this.httpResponse = httpRequest.response();
  }

  public void writeHeaders(
    String contentType,
    MultiMap grpcHeaders,
    boolean trailersOnly,
    GrpcStatus status,
    String stateMessage, String encoding) {
    encodeGrpcHeaders(httpResponse.headers(), contentType, grpcHeaders, trailersOnly, status, stateMessage, encoding);
  }

  protected void encodeGrpcHeaders(
    MultiMap httpHeaders,
    String contentType,
    MultiMap grpcHeaders,
    boolean trailersOnly,
    GrpcStatus status,
    String stateMessage, String encoding) {
    httpHeaders.set("content-type", contentType);
    encodeGrpcHeaders(grpcHeaders, httpHeaders, encoding);
    if (trailersOnly) {
      encodeGrpcStatus(httpHeaders, status, stateMessage);
    }
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
  protected void encodeGrpcStatus(MultiMap entries, GrpcStatus status, String statusMessage) {
    if (!entries.contains(GrpcHeaderNames.GRPC_STATUS)) {
      entries.set(GrpcHeaderNames.GRPC_STATUS, status.toString());
    }
    if (status != GrpcStatus.OK) {
      String msg = statusMessage;
      if (msg != null && !entries.contains(GrpcHeaderNames.GRPC_MESSAGE)) {
        entries.set(GrpcHeaderNames.GRPC_MESSAGE, Utils.utf8PercentEncode(msg));
      }
    } else {
      entries.remove(GrpcHeaderNames.GRPC_MESSAGE);
    }
  }

  public void writeTrailers(boolean trailersOnly, MultiMap grpcTrailers, GrpcStatus status, String statusMessage) {
    MultiMap httpTrailers;
    if (trailersOnly) {
      httpTrailers = httpResponse.headers();
    } else {
      httpTrailers = httpResponse.trailers();
    }
    encodeGrpcTrailers(grpcTrailers, httpTrailers);
    encodeGrpcStatus(httpTrailers, status, statusMessage);
  }

  protected final void encodeGrpcTrailers(MultiMap grpcTrailers, MultiMap httpTrailers) {
    if (grpcTrailers != null && !grpcTrailers.isEmpty()) {
      for (Map.Entry<String, String> header : grpcTrailers) {
        httpTrailers.add(header.getKey(), header.getValue());
      }
    }
  }

  public Future<Void> writeHead() {
    return httpResponse.writeHead();
  }

  public Future<Void> writeEnd(GrpcStatus status) {
    return httpResponse.end();
  }

  public Future<Void> writeMessage(GrpcMessage message) {
    Buffer payload;
    try {
      payload = message.payload();
    } catch (CodecException e) {
      return contextInternal.failedFuture(e);
    }
    return httpResponse.write(encodeMessage(payload, message.isCompressed(), false));
  }

  protected Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    return GrpcMessageImpl.encode(message, compressed, trailer);
  }
}
