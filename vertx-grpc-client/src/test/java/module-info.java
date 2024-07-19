open module io.vertx.tests.client {
  requires io.grpc;
  requires io.grpc.stub;
  requires io.grpc.util;
  requires io.vertx.core;
  requires io.vertx.grpc.client;
  requires io.vertx.grpc.common;
  requires io.vertx.testing.unit;
  requires io.vertx.tests.common;
  requires junit;
    requires com.google.protobuf;
}
