module io.vertx.grpc.common {

  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;
  requires static io.vertx.docgen;

  requires io.vertx.core;
  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.codec.compression;
  requires io.netty.transport;
  requires com.google.protobuf;
  requires com.google.protobuf.util;

  uses io.vertx.grpc.common.GrpcCompressor;
  uses io.vertx.grpc.common.GrpcDecompressor;

  exports io.vertx.grpc.common;
  exports io.vertx.grpc.common.impl to io.vertx.tests.common, io.vertx.grpc.server, io.vertx.grpc.client, io.vertx.grpc.transcoding, io.vertx.tests.server, io.vertx.tests.client;

  provides io.vertx.core.spi.VertxServiceProvider with io.vertx.grpc.common.impl.GrpcRequestLocalRegistration;
  provides io.vertx.grpc.common.GrpcCompressor with io.vertx.grpc.common.impl.IdentityCompressor;
  provides io.vertx.grpc.common.GrpcDecompressor with io.vertx.grpc.common.impl.IdentityCompressor;
}
