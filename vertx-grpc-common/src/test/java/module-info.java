open module io.vertx.tests.common {
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.testing.unit;
  requires junit;
    requires io.netty.codec.http;
    exports io.vertx.tests.common;
}
