import io.vertx.grpc.server.impl.GrpcHttpInvoker;

module io.vertx.grpc.transcoding {
  requires com.google.protobuf;
  requires com.google.protobuf.util;

  requires io.netty.codec;

  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;

  requires static io.vertx.codegen.api;

  exports io.vertx.grpc.transcoding;
  exports io.vertx.grpc.transcoding.impl.config to io.vertx.grpc.transcoding.tests;
  exports io.vertx.grpc.transcoding.impl to io.vertx.grpc.transcoding.tests;
  provides GrpcHttpInvoker with io.vertx.grpc.transcoding.impl.TranscodingInvoker;
}
