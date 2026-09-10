package io.vertx.grpc.common;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.impl.DefaultMessageEncoder;
import io.vertx.grpc.common.impl.DefaultJsonMessageEncoder;

import java.util.EnumSet;

public interface GrpcMessageEncoder<T> {

  /**
   * Create an encoder for arbitrary message extending {@link MessageLite}.
   * @return the message encoder
   */
  @GenIgnore
  static <T extends MessageLite> GrpcMessageEncoder<T> encoder() {
    return new DefaultMessageEncoder<>(JsonWriterConfig.DEFAULT, EnumSet.allOf(WireFormat.class));
  }

  GrpcMessageEncoder<Buffer> IDENTITY = new GrpcMessageEncoder<>() {
    @Override
    public GrpcMessage encode(Buffer msg, WireFormat format) throws CodecException {
      return GrpcMessage.message("identity", format, msg);
    }
    @Override
    public boolean accepts(WireFormat format) {
      return true;
    }
  };

  /**
   * Create and reutrn an encoder in JSON format encoding instances of {@link MessageOrBuilder} using the protobuf-java-util library
   * otherwise using {@link Json#encodeToBuffer(Object)} (Jackson Databind is required).
   *
   * @return an encoder in JSON format encoding instances of {@code <T>}.
   */
  static <T> GrpcMessageEncoder<T> json() {
    return new DefaultJsonMessageEncoder<>(JsonWriterConfig.DEFAULT);
  }

  /**
   * An encoder in JSON format encoding {@link JsonObject} instances.
   */
  GrpcMessageEncoder<JsonObject> JSON_OBJECT = new GrpcMessageEncoder<>() {
    @Override
    public GrpcMessage encode(JsonObject msg, WireFormat format) throws CodecException {
      return GrpcMessage.message("identity", format, msg == null ? Buffer.buffer("null") : msg.toBuffer());
    }
    @Override
    public boolean accepts(WireFormat format) {
      return format == WireFormat.JSON;
    }
  };

  GrpcMessage encode(T msg, WireFormat format) throws CodecException;

  boolean accepts(WireFormat format);

}
