package io.vertx.grpc.common;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public interface GrpcMessageEncoder<T> {

  /**
   * Create an encoder for arbitrary message extending {@link MessageLite}.
   * @return the message encoder
   */
  @GenIgnore
  static <T extends MessageLite> GrpcMessageEncoder<T> encoder() {
    return new GrpcMessageEncoder<T>() {
      @Override
      public GrpcMessage encode(T msg, WireFormat format) throws CodecException {
        if (format instanceof ProtobufWireFormat) {
          byte[] bytes = msg.toByteArray();
          return GrpcMessage.message("identity", format, Buffer.buffer(bytes));
        } else if (format instanceof JsonWireFormat) {
          JsonWireFormat json = (JsonWireFormat) format;
          if (msg instanceof MessageOrBuilder) {
            return GrpcMessage.message("identity", format, ProtobufJsonWriter.create(json.writerConfig()).write((MessageOrBuilder) msg));
          }
          return GrpcMessage.message("identity", format, Json.encodeToBuffer(msg));
        } else {
          throw new IllegalArgumentException("Invalid wire format: " + format);
        }
      }
      @Override
      public boolean accepts(WireFormat format) {
        return true;
      }
    };
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
    return new GrpcMessageEncoder<>() {
      @Override
      public GrpcMessage encode(T msg, WireFormat format) throws CodecException {
        JsonWireFormat json = (JsonWireFormat) format;
        if (msg instanceof MessageOrBuilder) {
          return GrpcMessage.message("identity", format, ProtobufJsonWriter.create(json.writerConfig()).write((MessageOrBuilder) msg));
        }
        return GrpcMessage.message("identity", format, Json.encodeToBuffer(msg));
      }
      @Override
      public boolean accepts(WireFormat format) {
        return format instanceof JsonWireFormat;
      }
    };
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
      return format instanceof JsonWireFormat;
    }
  };

  GrpcMessage encode(T msg, WireFormat format) throws CodecException;

  boolean accepts(WireFormat format);

}
