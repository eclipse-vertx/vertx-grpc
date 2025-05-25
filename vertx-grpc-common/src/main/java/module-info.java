module io.vertx.grpc.common {

  requires static io.vertx.codegen.api;

  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.codec.compression;
  requires io.netty.transport;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires io.vertx.core;

  uses io.vertx.grpc.common.GrpcCompressor;
  uses io.vertx.grpc.common.GrpcDecompressor;

  exports io.vertx.grpc.common;
  exports io.vertx.grpc.common.impl to io.vertx.tests.common, io.vertx.grpc.server, io.vertx.grpc.client, io.vertx.grpc.transcoding, io.vertx.tests.server, io.vertx.tests.client;
  exports io.vertx.grpc.common.compression to io.vertx.grpc.client, io.vertx.grpc.server, io.vertx.grpc.transcoding, io.vertx.tests.client, io.vertx.tests.common, io.vertx.tests.server;

  provides io.vertx.core.spi.VertxServiceProvider with io.vertx.grpc.common.impl.GrpcRequestLocalRegistration;
  provides io.vertx.grpc.common.GrpcCompressor with io.vertx.grpc.common.compression.GzipCompressor, io.vertx.grpc.common.compression.IdentityCompressor, io.vertx.grpc.common.compression.SnappyCompressor;
  provides io.vertx.grpc.common.GrpcDecompressor with io.vertx.grpc.common.compression.GzipCompressor, io.vertx.grpc.common.compression.IdentityCompressor, io.vertx.grpc.common.compression.SnappyCompressor;
}
