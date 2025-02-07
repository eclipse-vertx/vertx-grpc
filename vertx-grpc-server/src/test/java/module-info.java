open module io.vertx.tests.server {
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires io.grpc;
  requires io.grpc.stub;
  requires io.grpc.util;
  requires io.grpc.protobuf;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires io.vertx.grpc.transcoding;
  requires io.vertx.testing.unit;
  requires io.vertx.tests.common;
  requires junit;
  requires testcontainers;
}
