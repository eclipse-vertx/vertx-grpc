open module io.vertx.grpc.transcoding.tests {
  requires io.vertx.testing.unit;
  requires junit;
  requires io.grpc;
  requires io.grpc.stub;
  requires io.grpc.util;
  requires io.grpc.protobuf;
  requires io.vertx.core;
  requires io.vertx.grpc.transcoding;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires io.vertx.grpc.common.tests;
  requires io.vertx.grpc.server.tests;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires jmh.core;
  exports io.vertx.grpc.transcoding.tests;
}
