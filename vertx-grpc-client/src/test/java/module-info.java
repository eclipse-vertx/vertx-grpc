open module io.vertx.grpc.client.tests {
  requires io.grpc;
  requires io.grpc.stub;
  requires io.grpc.util;
  requires io.grpc.protobuf;
  requires io.vertx.core;
  requires io.vertx.grpc.client;
  requires io.vertx.grpc.common;
  requires io.vertx.testing.unit;
  requires io.vertx.grpc.common.tests;
  requires junit;
  requires com.google.protobuf;
  requires com.google.common;
}
