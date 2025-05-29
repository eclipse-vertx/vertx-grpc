package io.vertx.grpc.common;

import io.vertx.core.streams.Pipe;

public interface GrpcResponseTransformer extends Pipe<GrpcMessage> {
}
