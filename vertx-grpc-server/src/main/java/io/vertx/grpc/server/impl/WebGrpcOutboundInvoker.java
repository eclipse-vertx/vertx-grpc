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
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Map;

import static io.vertx.grpc.server.GrpcProtocol.WEB_TEXT;

public class WebGrpcOutboundInvoker extends HttpGrpcOutboundInvoker {

  private final GrpcProtocol protocol;
  private final HttpServerResponse httpResponse;
  private Buffer trailers;

  public WebGrpcOutboundInvoker(HttpServerRequest httpRequest, GrpcProtocol protocol, GrpcMessageDeframer deframer) {
    super(httpRequest, protocol, deframer);

    this.httpResponse = httpRequest.response();
    this.protocol = protocol;
  }

  public static Buffer grpcWebEncode(Buffer message) {
    return BufferInternal.buffer(Base64.encode(((BufferInternal)message).getByteBuf(), false));
  }

  @Override
  public Future<Void> writeHeaders(GrpcHeadersFrame frame) {
    httpResponse.setChunked(true);
    return super.writeHeaders(frame);
  }

  @Override
  public Future<Void> writeEnd() {
    if (trailers != null) {
      Future<Void> ret = httpResponse.end(encodeMessage(trailers, false, true));
      trailers = null;
      return ret;
    } else {
      return httpResponse.end();
    }
  }

  @Override
  public void writeTrailers(boolean useHeaders, MultiMap grpcTrailers, GrpcStatus status, String statusMessage) {
    if (useHeaders) {
      MultiMap httpHeaders = httpResponse.headers();
      encodeGrpcTrailers(grpcTrailers, httpHeaders);
      encodeGrpcStatus(httpHeaders, status, statusMessage);
    } else {
      MultiMap buffer = HttpHeaders.headers();
      encodeGrpcStatus(buffer, status, statusMessage);
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
