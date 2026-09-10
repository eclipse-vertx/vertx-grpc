package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.JsonReaderConfig;

public interface JsonGrpcMessageDecoder<T> extends GrpcMessageDecoder<T> {

  JsonGrpcMessageDecoder<T> configure(JsonReaderConfig config);

}
