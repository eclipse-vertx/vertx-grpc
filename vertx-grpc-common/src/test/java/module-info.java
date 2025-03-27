open module io.vertx.tests.common {
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.testing.unit;
  requires junit;
  exports io.vertx.tests.common;
  exports io.vertx.tests.common.grpc;
}
