package io.vertx.grpc.eventbus.impl;

import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.ProtobufJsonReader;
import io.vertx.grpc.common.impl.ProtobufJsonWriter;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

public class EventBusGrpcJsonMessageCodec implements MessageCodec<TransportFrame, TransportFrame> {

  public static final String CODEC_NAME = "grpc-event-bus-json-codec";

  public static final EventBusGrpcJsonMessageCodec INSTANCE = new EventBusGrpcJsonMessageCodec();

  private EventBusGrpcJsonMessageCodec() {
  }

  @Override
  public void encodeToWire(Buffer buffer, TransportFrame transportFrame) {
    Buffer data = ProtobufJsonWriter.create(WireFormat.JSON).write(transportFrame);
    buffer.appendInt(data.length());
    buffer.appendBuffer(data);
  }

  @Override
  public TransportFrame decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    Buffer data = buffer.getBuffer(pos, pos + length);
    TransportFrame.Builder builder = TransportFrame.newBuilder();
    ProtobufJsonReader.create(WireFormat.JSON).merge(data, builder);
    try {
      return builder.build();
    } catch (Exception e) {
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
