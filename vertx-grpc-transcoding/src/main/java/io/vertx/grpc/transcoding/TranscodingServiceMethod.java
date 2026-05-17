package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
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
    return new TranscodingServiceMethodImpl<>(serviceName, methodName, encoder, decoder, options, false);
  }

  static <Req, Resp> TranscodingServiceMethod<Req, Resp> server(ServiceName serviceName,
                                                                String methodName,
                                                                GrpcMessageEncoder<Resp> encoder,
                                                                GrpcMessageDecoder<Req> decoder,
                                                                MethodTranscodingOptions options,
                                                                boolean streaming) {
    return new TranscodingServiceMethodImpl<>(serviceName, methodName, encoder, decoder, options, streaming);
  }

  static <Req, Resp> TranscodingServiceMethod<Req, Resp> server(ServiceMethod<Req, Resp> serviceMethod,
                                                                MethodTranscodingOptions options) {
    return server(serviceMethod.serviceName(), serviceMethod.methodName(), serviceMethod.encoder(), serviceMethod.decoder(), options);
  }

  MethodTranscodingOptions options();

  /**
   * @return whether the response side of the RPC is streaming (server-streaming or bidi). When {@code true} the
   * transcoder emits a JSON array of messages (one element per gRPC message) on the HTTP response, using chunked
   * transfer encoding instead of a single content-length-framed JSON object.
   */
  default boolean streaming() {
    return false;
  }

}
