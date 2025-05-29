package io.vertx.grpc.common;

import io.vertx.core.Future;

import java.util.function.Function;

public interface GrpcRequestTransformer extends Function<GrpcMessage, Future<GrpcMessage>> {

}
