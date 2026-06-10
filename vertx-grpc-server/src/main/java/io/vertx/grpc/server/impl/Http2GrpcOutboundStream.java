package io.vertx.grpc.server.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.server.GrpcProtocol;

public class Http2GrpcOutboundStream extends HttpGrpcOutboundStream {

  public Http2GrpcOutboundStream(HttpServerRequest httpRequest, GrpcMessageDeframer deframer) {
    super(httpRequest, GrpcProtocol.HTTP_2, deframer);
  }

  @Override
  protected String contentType(WireFormat wireFormat) {
    return wireFormat.mediaType();
  }

  @Override
  protected void encodeGrpcHeaders(MultiMap grpcHeaders, MultiMap httpHeaders, String encoding) {
    super.encodeGrpcHeaders(grpcHeaders, httpHeaders, encoding);
    httpHeaders.set(GrpcHeaderNames.GRPC_ENCODING, encoding);
    httpHeaders.set(GrpcHeaderNames.GRPC_ACCEPT_ENCODING, "gzip");
  }
}
