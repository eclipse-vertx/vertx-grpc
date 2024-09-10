module io.vertx.grpc.server {
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.vertx.core.logging;
  requires transitive io.vertx.grpc.common;
  requires static io.vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;
  requires com.google.protobuf;
  exports io.vertx.grpc.server;
}
