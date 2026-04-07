open module io.vertx.tests.eventbus {
  requires io.vertx.core;
  requires io.vertx.grpc.client;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.eventbus;
  requires io.vertx.testing.unit;
  requires io.vertx.tests.common;

  requires junit;

  requires com.google.protobuf;
  requires com.google.common;
}
