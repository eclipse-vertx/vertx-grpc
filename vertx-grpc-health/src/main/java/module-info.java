module io.vertx.grpc.health {

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.json;

  requires java.logging;
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires io.vertx.codegen.api;
  requires com.google.protobuf;

  exports io.vertx.grpc.health;
}
