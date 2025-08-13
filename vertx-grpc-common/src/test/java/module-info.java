open module io.vertx.tests.common {
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.testing.unit;
  requires junit;
  requires com.google.common;
  requires com.google.protobuf;
  requires io.grpc;
  requires io.grpc.protobuf;
  requires io.grpc.stub;

  uses io.vertx.grpc.common.GrpcCompressor;
  uses io.vertx.grpc.common.GrpcDecompressor;

  exports io.vertx.tests.common;
  exports io.vertx.tests.common.grpc;
}
