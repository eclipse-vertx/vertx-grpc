module io.vertx.grpc.reflection {
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires static io.vertx.docgen;
  requires static io.vertx.codegen.json;
  requires io.vertx.codegen.api;
  requires com.google.protobuf;
  exports io.vertx.grpc.reflection;
}
