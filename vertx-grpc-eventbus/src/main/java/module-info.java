module io.vertx.grpc.eventbus {
  requires io.vertx.core;
  requires io.vertx.core.logging;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.client;
  requires io.vertx.grpc.server;
  requires com.google.protobuf;

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  exports io.vertx.grpc.eventbus;
  exports io.vertx.grpc.eventbus.impl to io.vertx.tests.eventbus;

  opens io.vertx.grpc.eventbus.transport.v1alpha to com.google.protobuf;
}
