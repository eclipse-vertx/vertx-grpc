package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.transcoding.impl.TranscodingServiceMethodImpl;

@VertxGen
public interface TranscodingServiceMethod<I, O> extends ServiceMethod<I, O> {

  static <Req, Resp> TranscodingServiceMethod<Req, Resp> server(ServiceName serviceName,
                                                                String methodName,
                                                                GrpcMessageEncoder<Resp> encoder,
                                                                GrpcMessageDecoder<Req> decoder,
                                                                MethodTranscodingOptions options) {
    return new TranscodingServiceMethodImpl<>(serviceName, methodName, encoder, decoder, options);
  }

  MethodTranscodingOptions options();

}
