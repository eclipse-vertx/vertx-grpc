package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

public class EventBusGrpcProtobufMessageCodec implements MessageCodec<TransportFrame, TransportFrame> {

  public static final String CODEC_NAME = "grpc-event-bus-protobuf-codec";

  public static final EventBusGrpcProtobufMessageCodec INSTANCE = new EventBusGrpcProtobufMessageCodec();

  private EventBusGrpcProtobufMessageCodec() {
  }

  @Override
  public void encodeToWire(Buffer buffer, TransportFrame transportFrame) {
    Buffer data = Buffer.buffer(transportFrame.toByteArray());
    buffer.appendInt(data.length());
    buffer.appendBuffer(data);
  }

  @Override
  public TransportFrame decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] data = buffer.getBytes(pos, pos + length);
    try {
      return TransportFrame.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      throw new VertxException(e);
    }
  }

  @Override
  public TransportFrame transform(TransportFrame transportFrame) {
    return transportFrame;
  }

  @Override
  public String name() {
    return CODEC_NAME;
  }

  @Override
  public byte systemCodecID() {
    return -1;
  }
}
