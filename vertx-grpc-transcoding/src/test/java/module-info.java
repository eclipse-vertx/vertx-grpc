open module io.vertx.tests.transcoding {
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
  requires io.vertx.tests.common;
  requires io.vertx.tests.server;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires jmh.core;
  exports io.vertx.tests.transcoding;
}
