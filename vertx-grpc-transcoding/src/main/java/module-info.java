module io.vertx.grpc.transcoding {
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires com.google.common;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires io.vertx.codegen.api;
  exports io.vertx.grpc.transcoding;
  exports io.vertx.grpc.transcoding.impl.config to io.vertx.tests.transcoding;
  exports io.vertx.grpc.transcoding.impl to io.vertx.tests.transcoding;
}
