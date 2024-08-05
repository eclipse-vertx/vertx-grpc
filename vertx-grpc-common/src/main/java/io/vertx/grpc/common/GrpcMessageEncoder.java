package io.vertx.grpc.common;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

@VertxGen
public interface GrpcMessageEncoder<T> {

  /**
   * Create an encoder for arbitrary message extending {@link MessageLite}.
   * @return the message encoder
   */
  @GenIgnore
  static <T extends MessageLite> GrpcMessageEncoder<T> encoder() {
    return new GrpcMessageEncoder<T>() {
      @Override
      public GrpcMessage encode(T msg) {
        byte[] bytes = msg.toByteArray();
        return GrpcMessage.message("identity", Buffer.buffer(bytes));
      }
      @Override
      public WireFormat format() {
        return WireFormat.PROTOBUF;
      }
    };
  }

  GrpcMessageEncoder<Buffer> IDENTITY = new GrpcMessageEncoder<>() {
    @Override
    public GrpcMessage encode(Buffer payload) {
      return GrpcMessage.message("identity", WireFormat.PROTOBUF, payload);
    }
    @Override
    public WireFormat format() {
      return WireFormat.PROTOBUF;
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
      public GrpcMessage encode(T msg) {
        if (msg instanceof MessageOrBuilder) {
          MessageOrBuilder mob = (MessageOrBuilder) msg;
          try {
            String res = JsonFormat.printer().print(mob);
            return GrpcMessage.message("identity", WireFormat.JSON, Buffer.buffer(res));
          } catch (InvalidProtocolBufferException e) {
            throw new CodecException(e);
          }
        }
        return GrpcMessage.message(
          "identity",
          WireFormat.JSON,
          Json.encodeToBuffer(msg));
      }
      @Override
      public WireFormat format() {
        return WireFormat.JSON;
      }
    };
  }

  /**
   * An encoder in JSON format encoding {@link JsonObject} instances.
   */
  GrpcMessageEncoder<JsonObject> JSON_OBJECT = new GrpcMessageEncoder<>() {
    @Override
    public GrpcMessage encode(JsonObject msg) {
      return GrpcMessage.message("identity", WireFormat.JSON, msg == null ? Buffer.buffer("null") : msg.toBuffer());
    }
    @Override
    public WireFormat format() {
      return WireFormat.JSON;
    }
  };

  GrpcMessage encode(T msg);

  WireFormat format();

}
