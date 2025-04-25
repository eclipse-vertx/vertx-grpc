module io.vertx.grpc.server {

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.json;

  requires io.vertx.core.logging;
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.codegen.api;
  requires io.netty.codec;
  requires io.netty.buffer;
  requires com.google.protobuf;

  uses io.vertx.grpc.server.impl.GrpcHttpInvoker;

  exports io.vertx.grpc.server;
  exports io.vertx.grpc.server.impl to io.vertx.grpc.transcoding;
}
