package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.grpc.common.*;
import io.vertx.grpc.transcoding.impl.TranscodingServiceMethodImpl;

@GenIgnore(GenIgnore.PERMITTED_TYPE)
@Unstable("Transcoding is in tech preview")
public interface TranscodingServiceMethod<I, O> extends ServiceMethod<I, O> {

  static <Req, Resp> TranscodingServiceMethod<Req, Resp> server(ServiceName serviceName,
                                                                String methodName,
                                                                GrpcMessageEncoder<Resp> encoder,
                                                                GrpcMessageDecoder<Req> decoder) {
    return new TranscodingServiceMethodImpl<>(serviceName, methodName, encoder, decoder);
  }

  static <Req, Resp> TranscodingServiceMethod<Req, Resp> server(ServiceName serviceName,
                                                                String methodName,
                                                                GrpcMessageEncoder<Resp> encoder,
                                                                GrpcMessageDecoder<Req> decoder,
                                                                MethodTranscodingOptions options) {
    return new TranscodingServiceMethodImpl<>(serviceName, methodName, encoder, decoder, options);
  }

  static <Req, Resp> TranscodingServiceMethod<Req, Resp> server(ServiceName serviceName,
                                                                String methodName,
                                                                MethodCardinality cardinality,
                                                                GrpcMessageEncoder<Resp> encoder,
                                                                GrpcMessageDecoder<Req> decoder,
                                                                MethodTranscodingOptions options) {
    return new TranscodingServiceMethodImpl<>(serviceName, methodName, cardinality, encoder, decoder, options);
  }

  static <Req, Resp> TranscodingServiceMethod<Req, Resp> server(ServiceMethod<Req, Resp> serviceMethod,
                                                                MethodTranscodingOptions options) {
    return server(serviceMethod.serviceName(), serviceMethod.methodName(), serviceMethod.cardinality(), serviceMethod.encoder(), serviceMethod.decoder(), options);
  }

  MethodTranscodingOptions options();

}
