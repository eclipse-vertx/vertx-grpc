module io.vertx.grpc.client{

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  requires io.netty.buffer;
  requires io.netty.codec.http;
  requires io.netty.codec;
  requires io.vertx.core.logging;
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires com.google.protobuf;
  requires com.google.common;

  uses io.vertx.grpc.common.GrpcCompressor;
  uses io.vertx.grpc.common.GrpcDecompressor;

  exports io.vertx.grpc.client;
  exports io.vertx.grpc.client.impl to io.vertx.tests.client;
}
