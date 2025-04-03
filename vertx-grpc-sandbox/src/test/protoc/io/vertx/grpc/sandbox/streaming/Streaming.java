package io.vertx.grpc.sandbox.streaming;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.ServiceBuilder;
import io.vertx.codegen.annotations.GenIgnore;

import com.google.protobuf.Descriptors;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Base definition Streaming service.</p>
 */
@io.vertx.codegen.annotations.VertxGen
@io.vertx.grpc.common.annotations.GrpcClass(packageName = "streaming", name = "Streaming")
public interface Streaming {


  @io.vertx.grpc.common.annotations.GrpcMethod(name = "Unary")
  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<io.vertx.grpc.sandbox.streaming.Item> unary(io.vertx.grpc.sandbox.streaming.Item request);

  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> source(io.vertx.grpc.sandbox.streaming.Empty request);

  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<io.vertx.grpc.sandbox.streaming.Empty> sink(ReadStream<io.vertx.grpc.sandbox.streaming.Item> request);

  @io.vertx.codegen.annotations.GenIgnore(io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE)
  Future<ReadStream<io.vertx.grpc.sandbox.streaming.Item>> pipe(ReadStream<io.vertx.grpc.sandbox.streaming.Item> request);
}
