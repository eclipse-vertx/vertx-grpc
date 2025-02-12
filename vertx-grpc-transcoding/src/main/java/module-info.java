module io.vertx.grpc.transcoding {
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires com.google.common;
  requires io.vertx.core;
  requires io.vertx.codegen.api;
  exports io.vertx.grpc.transcoding to io.vertx.tests.common, io.vertx.grpc.server, io.vertx.grpc.client, io.vertx.tests.server, io.vertx.tests.client, io.vertx.tests.transcoding;
}
