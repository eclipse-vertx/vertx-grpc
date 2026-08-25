package io.vertx.grpc.server.impl;

import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.impl.GrpcMethod;
import io.vertx.grpc.common.impl.GrpcStream;

public class GrpcMethodCall<Req, Resp> extends GrpcMethod {

  private final GrpcStream stream;
  private final GrpcMessageDecoder<Req> messageDecoder;
  private final GrpcMessageEncoder<Resp> messageEncoder;

  public GrpcMethodCall(String path,
                        GrpcStream stream,
                        GrpcMessageDecoder<Req> messageDecoder,
                        GrpcMessageEncoder<Resp> messageEncoder) {
    super(path);

    this.stream = stream;
    this.messageDecoder = messageDecoder;
    this.messageEncoder = messageEncoder;
  }

  public GrpcStream stream() {
    return stream;
  }

  public GrpcMessageDecoder<Req> messageDecoder() {
    return messageDecoder;
  }

  public GrpcMessageEncoder<Resp> messageEncoder() {
    return messageEncoder;
  }
}
