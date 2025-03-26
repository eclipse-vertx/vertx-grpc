module io.vertx.grpcio.common {
  exports io.vertx.grpcio.common.impl;
  requires io.vertx.grpc.common;
  requires io.netty.buffer;
  requires io.netty.common;
  requires io.grpc;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
}
