package io.vertx.grpc.server.impl;

import io.netty.handler.codec.base64.Base64;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Map;

import static io.vertx.grpc.server.GrpcProtocol.WEB_TEXT;

public class WebProtocolHandler extends ProtocolHandler {

  private final GrpcProtocol protocol;
  private final HttpServerResponse httpResponse;
  private Buffer trailers;

  public WebProtocolHandler(HttpServerRequest httpRequest, GrpcProtocol protocol) {
    super(httpRequest);

    this.httpResponse = httpRequest.response();
    this.protocol = protocol;
  }

  public static Buffer grpcWebEncode(Buffer message) {
    return BufferInternal.buffer(Base64.encode(((BufferInternal)message).getByteBuf(), false));
  }

  @Override
  protected void encodeGrpcHeaders(MultiMap httpHeaders, String contentType, MultiMap grpcHeaders, boolean trailersOnly, GrpcStatus status, String stateMessage, String encoding) {
    super.encodeGrpcHeaders(httpHeaders, contentType, grpcHeaders, trailersOnly, status, stateMessage, encoding);
    if (!trailersOnly) {
      httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
    }
  }

  @Override
  protected Future<Void> sendEnd(GrpcStatus status) {
    if (trailers != null) {
      Future<Void> ret = httpResponse.end(encodeMessage(trailers, false, true));
      trailers = null;
      return ret;
    } else {
      return httpResponse.end();
    }
  }

  @Override
  protected void encodeGrpcTrailers(boolean trailersOnly, MultiMap grpcTrailers, GrpcStatus status, String grpcMessage) {
    if (trailersOnly) {
      if (grpcTrailers != null) {
        encodeGrpcTrailers(grpcTrailers, httpResponse.headers());
      }
    } else {
      MultiMap buffer = HttpHeaders.headers();
      super.encodeGrpcStatus(buffer, status, grpcMessage);
      appendToTrailers(buffer);
      if (grpcTrailers != null) {
        appendToTrailers(grpcTrailers);
      }
    }
  }

  private void appendToTrailers(MultiMap entries) {
    if (trailers == null) {
      trailers = Buffer.buffer();
    }
    for (Map.Entry<String, String> trailer : entries) {
      trailers.appendString(trailer.getKey())
        .appendByte((byte) ':')
        .appendString(trailer.getValue())
        .appendString("\r\n");
    }
  }

  protected Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    message = super.encodeMessage(message, compressed, trailer);
    if (protocol == WEB_TEXT) {
      message = grpcWebEncode(message);
    }
    return message;
  }
}
