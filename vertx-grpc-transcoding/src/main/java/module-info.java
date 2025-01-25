module io.vertx.grpc.transcoding {
  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.codec.compression;
  requires io.netty.transport;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires transitive io.vertx.core;
  requires static io.vertx.codegen.api;
  requires com.google.common;
  exports io.vertx.grpc.transcoding to io.vertx.tests.common, io.vertx.grpc.server, io.vertx.grpc.client, io.vertx.tests.server, io.vertx.tests.client, io.vertx.tests.transcoding;
}
