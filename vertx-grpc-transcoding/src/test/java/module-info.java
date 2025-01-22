open module io.vertx.tests.transcoding {
  requires io.vertx.testing.unit;
  requires junit;
    requires io.netty.codec.http;
  requires io.vertx.grpc.transcoding;
  exports io.vertx.tests.transcoding;
}
