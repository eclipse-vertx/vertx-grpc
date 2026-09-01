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
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.DefaultGrpcMessage;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Map;

import static io.vertx.grpc.server.GrpcProtocol.WEB_TEXT;

public class WebGrpcOutboundStream extends HttpGrpcOutboundStream {

  private final GrpcProtocol protocol;
  private final HttpServerResponse httpResponse;
  private Buffer trailers;

  public WebGrpcOutboundStream(HttpServerRequest httpRequest, GrpcProtocol protocol, GrpcMessageDeframer deframer) {
    super(httpRequest, protocol, deframer);

    this.httpResponse = httpRequest.response();
    this.protocol = protocol;
  }

  @Override
  protected String contentType(WireFormat wireFormat) {
    return protocol.mediaType();
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

  protected Buffer encodeMessage(Buffer message, boolean compressed) {
    return encodeMessage(message, compressed, false);
  }

  private Buffer encodeMessage(Buffer message, boolean compressed, boolean trailer) {
    message = encode(message, compressed, trailer);
    if (protocol == WEB_TEXT) {
      message = grpcWebEncode(message);
    }
    return message;
  }

  /**
   * Encode a gRPC-Web message;
   *
   * @param payload the message
   * @param compressed wether the message is compressed
   * @param trailer whether this message is a gRPC-Web trailer
   * @return the encoded message
   */
  private static BufferInternal encode(Buffer payload, boolean compressed, boolean trailer) {
    int len = payload.length();
    BufferInternal encoded = BufferInternal.buffer(5 + len);
    encoded.appendByte((byte) ((trailer ? 0x80 : 0x00) | (compressed ? 0x01 : 0x00)));
    encoded.appendInt(len);
    encoded.appendBuffer(payload);
    return encoded;
  }
}
