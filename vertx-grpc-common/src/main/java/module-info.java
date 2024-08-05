import io.vertx.core.spi.VertxServiceProvider;

module io.vertx.grpc.common {
  requires com.google.protobuf;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.common;
  requires io.netty.transport;
  requires transitive io.vertx.core;
  requires static io.vertx.codegen.api;
    requires com.google.protobuf.util;
    exports io.vertx.grpc.common;
  exports io.vertx.grpc.common.impl to io.vertx.tests.common, io.vertx.grpc.server, io.vertx.grpc.client, io.vertx.tests.server, io.vertx.tests.client;
  provides VertxServiceProvider with io.vertx.grpc.common.impl.GrpcRequestLocalRegistration;
}
