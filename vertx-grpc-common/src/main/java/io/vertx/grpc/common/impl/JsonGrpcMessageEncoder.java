package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.JsonWriterConfig;

public interface JsonGrpcMessageEncoder<T> extends GrpcMessageEncoder<T> {

  JsonGrpcMessageEncoder<T> configure(JsonWriterConfig config);

}
