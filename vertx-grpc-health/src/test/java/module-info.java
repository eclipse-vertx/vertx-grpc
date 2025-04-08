open module io.vertx.tests.health {
  requires com.google.common;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires io.grpc;
  requires io.grpc.stub;
  requires io.grpc.util;
  requires io.grpc.protobuf;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires io.vertx.grpc.health;
  requires io.vertx.testing.unit;
  requires io.vertx.tests.common;
  requires io.vertx.tests.server;
  requires junit;
  requires testcontainers;
  requires io.vertx.healthcheck;
  requires io.vertx.core;
  exports io.vertx.tests.health.grpc;
}
