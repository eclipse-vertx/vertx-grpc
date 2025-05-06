import io.vertx.grpc.server.impl.GrpcHttpInvoker;

module io.vertx.grpc.transcoding {
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires com.google.common;
  requires io.vertx.core;
  requires io.vertx.grpc.common;
  requires io.vertx.grpc.server;
  requires static io.vertx.codegen.api;
  requires io.netty.codec;
  exports io.vertx.grpc.transcoding;
  exports io.vertx.grpc.transcoding.impl.config to io.vertx.tests.transcoding;
  exports io.vertx.grpc.transcoding.impl to io.vertx.tests.transcoding;
  provides GrpcHttpInvoker with io.vertx.grpc.transcoding.impl.TranscodingInvoker;
}
