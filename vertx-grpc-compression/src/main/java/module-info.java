module io.vertx.grpc.compression {

  requires static io.vertx.codegen.api;

  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.codec.compression;
  requires io.netty.transport;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires io.vertx.core;
  requires io.vertx.grpc.common;

  exports io.vertx.grpc.compression to io.vertx.grpc.client, io.vertx.grpc.server, io.vertx.grpc.transcoding, io.vertx.tests.client, io.vertx.tests.common, io.vertx.tests.server;

  provides io.vertx.grpc.common.GrpcCompressor with io.vertx.grpc.compression.GzipCompressor, io.vertx.grpc.compression.SnappyCompressor;
  provides io.vertx.grpc.common.GrpcDecompressor with io.vertx.grpc.compression.GzipCompressor, io.vertx.grpc.compression.SnappyCompressor;
}
