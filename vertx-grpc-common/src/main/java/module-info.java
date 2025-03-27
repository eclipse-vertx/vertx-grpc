import io.vertx.core.spi.VertxServiceProvider;

module io.vertx.grpc.common {
  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.codec.compression;
  requires io.netty.transport;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires io.vertx.core;
  requires static io.vertx.codegen.api;
  exports io.vertx.grpc.common;
  exports io.vertx.grpc.common.impl to io.vertx.tests.common, io.vertx.grpc.server, io.vertx.grpc.client, io.vertx.grpc.transcoding, io.vertx.tests.server, io.vertx.tests.client;
  provides VertxServiceProvider with io.vertx.grpc.common.impl.GrpcRequestLocalRegistration;
}
