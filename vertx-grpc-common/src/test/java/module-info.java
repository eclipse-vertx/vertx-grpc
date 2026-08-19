open module io.vertx.grpc.common.tests {
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.testing.unit;
  requires junit;
  requires com.google.common;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires io.grpc;
  requires io.grpc.protobuf;
  requires io.grpc.stub;
  exports io.vertx.grpc.common.tests;
}
