module io.vertx.grpc.client{
  requires io.grpc;
  requires io.grpc.stub;
  requires io.netty.buffer;
  requires io.netty.codec.http;
  requires io.netty.codec;
  requires io.vertx.core.logging;
  requires transitive io.vertx.grpc.common;
  requires static vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;
  requires io.grpc.protobuf;
  requires com.google.protobuf;
  requires com.google.common;
  exports io.vertx.grpc.client;
  exports io.vertx.grpc.client.impl to io.vertx.tests.client;
}
