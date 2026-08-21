package io.vertx.grpc.eventbus.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

final class EventBusGrpcCodec {

  private static final GrpcMessageEncoder<TransportFrame> FRAME_ENCODER = GrpcMessageEncoder.encoder();
  private static final GrpcMessageDecoder<TransportFrame> FRAME_DECODER = GrpcMessageDecoder.decoder(TransportFrame.getDefaultInstance());

  private EventBusGrpcCodec() {
  }

  static Object encodeBody(Buffer payload, WireFormat wireFormat) {
    if (wireFormat == WireFormat.JSON) {
      return payload.length() == 0 ? new JsonObject() : new JsonObject(payload);
    }
    return payload;
  }

  static Buffer decodeBody(Object body) {
    if (body instanceof Buffer) {
      return (Buffer) body;
    }
    if (body instanceof JsonObject) {
      return ((JsonObject) body).toBuffer();
    }
    if (body == null) {
      return Buffer.buffer();
    }
    throw new IllegalStateException("Unsupported event bus body type: " + body.getClass().getName());
  }

  static Buffer encodeFrame(TransportFrame frame, WireFormat format) {
    return FRAME_ENCODER.encode(frame, format).payload();
  }

  static TransportFrame decodeFrame(Message<Object> message) {
    String header = message.headers().get(EventBusHeaders.WIRE_FORMAT);
    WireFormat format = JsonWireFormat.NAME.equals(header) ? WireFormat.JSON : WireFormat.PROTOBUF;
    return FRAME_DECODER.decode(GrpcMessage.message("identity", format, decodeBody(message.body())));
  }

  static GrpcMessage message(TransportFrame frame, String encoding, WireFormat wireFormat) {
    Buffer buffer;
    switch (wireFormat.name()) {
      case "proto":
        buffer = Buffer.buffer(frame.getMessage().getBytes().toByteArray());
        break;
      case "json":
        buffer = Buffer.buffer(frame.getMessage().getString());
        break;
      default:
        throw new UnsupportedOperationException();
    }
    return GrpcMessage.message(encoding, wireFormat, buffer);
  }

  static GrpcStatus mapFailure(Throwable cause) {
    if (cause instanceof ReplyException) {
      ReplyException replyException = (ReplyException) cause;
      if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
        return GrpcStatus.UNAVAILABLE;
      }
      if (replyException.failureType() == ReplyFailure.TIMEOUT) {
        return GrpcStatus.DEADLINE_EXCEEDED;
      }
      GrpcStatus status = GrpcStatus.valueOf(replyException.failureCode());
      if (status != null) {
        return status;
      }
    }
    return GrpcStatus.INTERNAL;
  }
}
