package io.vertx.grpc.transcoding.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.impl.GrpcMessageFrame;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.impl.HttpGrpcOutboundInvoker;

public class TranscodingGrpcOutboundInvoker extends HttpGrpcOutboundInvoker {

  private Promise<Void> head;
  private final ContextInternal context;
  private final HttpServerResponse httpResponse;
  private final String transcodingResponseBody;

  public TranscodingGrpcOutboundInvoker(ContextInternal context, HttpServerRequest httpRequest, String transcodingResponseBody) {
    super(httpRequest);

    this.context = context;
    this.httpResponse = httpRequest.response();
    this.transcodingResponseBody = transcodingResponseBody;
  }

  @Override
  protected void encodeGrpcHeaders(MultiMap grpcHeaders, MultiMap httpHeaders, String encoding) {
  }

  @Override
  public Future<Void> writeEnd() {
    assert(status != null);
    if (status != GrpcStatus.OK) {
      httpResponse.setStatusCode(GrpcTranscodingError.fromHttp2Code(status.code).getHttpStatusCode());
    }
    return super.writeEnd();
  }

  @Override
  public Future<Void> writeHead() {
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
    Future<Void> res;
    try {
      BufferInternal transcoded = (BufferInternal) MessageWeaver.weaveResponseMessage(payload, transcodingResponseBody);
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
}
