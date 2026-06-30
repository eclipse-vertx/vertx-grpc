package io.vertx.grpc.transcoding.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.Json;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.impl.HttpGrpcOutboundStream;

public class TranscodingGrpcOutboundStream extends HttpGrpcOutboundStream {

  /**
   * Wire framing for server-streaming responses. Selected per-request from the {@code Accept} header so clients can opt into a format suited to their consumption pattern without
   * server reconfiguration. Mirrors Envoy's {@code GrpcJsonTranscoder.PrintOptions} (stream_newline_delimited, stream_sse_style_delimited).
   */
  private enum StreamFormat {
    /** Default: top-level JSON array, {@code application/json}. Envoy-compatible; not parseable until the stream ends. */
    JSON_ARRAY("application/json"),
    /** Newline-delimited JSON: one message per line. Each line is independently parseable. */
    NDJSON("application/x-ndjson"),
    /** Server-Sent Events: {@code data: <msg>\n\n} framing, consumable by browser {@code EventSource}. */
    SSE("text/event-stream");

    final String mediaType;

    StreamFormat(String mediaType) {
      this.mediaType = mediaType;
    }
  }

  private static final Buffer OPEN_ARRAY = Buffer.buffer("[");
  private static final Buffer EMPTY_ARRAY = Buffer.buffer("[]");
  private static final Buffer COMMA = Buffer.buffer(",");
  private static final Buffer CLOSE_ARRAY = Buffer.buffer("]");
  private static final Buffer NEWLINE = Buffer.buffer("\n");
  private static final Buffer SSE_PREFIX = Buffer.buffer("data: ");
  private static final Buffer SSE_SUFFIX = Buffer.buffer("\n\n");

  private Promise<Void> head;
  private final ContextInternal context;
  private final HttpServerResponse httpResponse;
  private final String transcodingResponseBody;
  private final boolean streaming;
  private final StreamFormat streamFormat;
  private boolean firstMessageWritten;

  public TranscodingGrpcOutboundStream(
    ContextInternal context,
    HttpServerRequest httpRequest,
    String transcodingResponseBody,
    GrpcMessageDeframer deframer,
    boolean streaming
  ) {
    super(httpRequest, GrpcProtocol.TRANSCODING, deframer);

    this.context = context;
    this.httpResponse = httpRequest.response();
    this.transcodingResponseBody = transcodingResponseBody;
    this.streaming = streaming;
    this.streamFormat = streaming ? negotiateStreamFormat(httpRequest.getHeader(HttpHeaders.ACCEPT)) : StreamFormat.JSON_ARRAY;
  }

  private static StreamFormat negotiateStreamFormat(String acceptHeader) {
    if (acceptHeader == null) {
      return StreamFormat.JSON_ARRAY;
    }
    String accept = acceptHeader.toLowerCase();
    if (accept.contains("text/event-stream")) {
      return StreamFormat.SSE;
    }
    if (accept.contains("application/x-ndjson") || accept.contains("application/jsonl")) {
      return StreamFormat.NDJSON;
    }
    return StreamFormat.JSON_ARRAY;
  }

  @Override
  protected String contentType(WireFormat wireFormat) {
    if (wireFormat instanceof JsonWireFormat) {
      return streaming ? streamFormat.mediaType : protocol.mediaType();
    }
    throw new UnsupportedOperationException();
  }

  @Override
  protected void encodeGrpcHeaders(MultiMap grpcHeaders, MultiMap httpHeaders, String encoding) {
  }

  @Override
  protected Future<Void> writeHeaders(GrpcHeadersFrame frame) {
    if (streaming) {
      // Flush headers up-front with chunked transfer so each message can be emitted as soon as it arrives.
      // The unary path defers writeHead until the first message so Content-Length can be set on the response.
      httpResponse.setChunked(true);
    }
    return super.writeHeaders(frame);
  }

  @Override
  public Future<Void> writeHead() {
    if (streaming) {
      return httpResponse.writeHead();
    }
    head = context.promise();
    return head.future();
  }

  @Override
  public Future<Void> writeMessage(GrpcMessageFrame frame) {
    Buffer payload;
    try {
      payload = frame.message().payload();
    } catch (CodecException e) {
      return context.failedFuture(e);
    }
    if (streaming) {
      return writeStreamingMessage(payload);
    }
    return writeUnaryMessage(payload);
  }

  private Future<Void> writeUnaryMessage(Buffer payload) {
    Future<Void> res;
    try {
      Buffer transcoded = Json.encodeToBuffer(MessageWeaver.weaveResponseMessage(payload, transcodingResponseBody));
      httpResponse.putHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(transcoded.length()));
      httpResponse.putHeader(HttpHeaders.CONTENT_TYPE, GrpcProtocol.TRANSCODING.mediaType());
      res = httpResponse.write(transcoded);
    } catch (Exception e) {
      httpResponse.setStatusCode(500).end();
      res = context.failedFuture(e);
    }
    if (head != null) {
      res.onComplete(head);
    }
    return res;
  }

  private Future<Void> writeStreamingMessage(Buffer payload) {
    Buffer transcoded;
    try {
      transcoded = Json.encodeToBuffer(MessageWeaver.weaveResponseMessage(payload, transcodingResponseBody));
    } catch (Exception e) {
      httpResponse.setStatusCode(500).end();
      return context.failedFuture(e);
    }
    Buffer chunk;
    switch (streamFormat) {
      case JSON_ARRAY: {
        Buffer prefix = firstMessageWritten ? COMMA : OPEN_ARRAY;
        chunk = Buffer.buffer(prefix.length() + transcoded.length()).appendBuffer(prefix).appendBuffer(transcoded);
        break;
      }
      case NDJSON: {
        chunk = Buffer.buffer(transcoded.length() + NEWLINE.length()).appendBuffer(transcoded).appendBuffer(NEWLINE);
        break;
      }
    case SSE: {
        chunk = Buffer.buffer(SSE_PREFIX.length() + transcoded.length() + SSE_SUFFIX.length())
          .appendBuffer(SSE_PREFIX).appendBuffer(transcoded).appendBuffer(SSE_SUFFIX);
        break;
      }
      default:
        throw new AssertionError(streamFormat);
    }
    firstMessageWritten = true;
    return httpResponse.write(chunk);
  }

  @Override
  public Future<Void> writeEnd() {
    if (streaming) {
      if (!firstMessageWritten && status != null && status != GrpcStatus.OK) {
        httpResponse.setStatusCode(GrpcTranscodingError.fromHttp2Code(status.code).getHttpStatusCode());
        return httpResponse.end();
      }
      if (streamFormat == StreamFormat.JSON_ARRAY) {
        return httpResponse.end(firstMessageWritten ? CLOSE_ARRAY : EMPTY_ARRAY);
      }
      return httpResponse.end();
    }
    if (status != GrpcStatus.OK) {
      httpResponse.setStatusCode(GrpcTranscodingError.fromHttp2Code(status.code).getHttpStatusCode());
    }
    return super.writeEnd();
  }
}
