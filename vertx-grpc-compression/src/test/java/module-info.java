import io.vertx.tests.compression.CustomCompressorTest;

open module io.vertx.tests.compression {
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.testing.unit;
  requires junit;

  uses io.vertx.grpc.common.GrpcCompressor;
  uses io.vertx.grpc.common.GrpcDecompressor;

  exports io.vertx.tests.compression;

  provides io.vertx.grpc.common.GrpcCompressor with CustomCompressorTest.ReverseCompressor;
  provides io.vertx.grpc.common.GrpcDecompressor with CustomCompressorTest.ReverseCompressor;
}
