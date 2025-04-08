module io.vertx.grpc.health {
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires io.vertx.healthcheck;
  requires static io.vertx.docgen;
  requires static io.vertx.codegen.json;
  requires io.vertx.codegen.api;
  requires com.google.protobuf;
  requires java.logging;
  exports io.vertx.grpc.health;
}
