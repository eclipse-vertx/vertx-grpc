package io.vertx.grpc.common.proto;

import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.proto.schema.Field;
import io.vertx.grpc.common.proto.schema.MessageType;
import io.vertx.grpc.common.proto.schema.WireType;

import java.nio.charset.StandardCharsets;
import java.util.Stack;
import java.util.function.Consumer;

public class ProtobufWriter {

  public static Buffer encode(Consumer<Visitor> consumer) {
    ProtobufWriter.State state = new ProtobufWriter.State();
    ComputePhase visitor = new ComputePhase();
    visitor.state = state;
    consumer.accept(visitor);
    EncodingPhase encoder = new EncodingPhase();
    encoder.state = state;
    consumer.accept(encoder);
    return state.buffer;
  }

  static class State {
    int[] capture = new int[100];
    Buffer buffer;
    byte[][] strings = new byte[100][];
  }

  static class ComputePhase implements Visitor {

    State state;
    Stack<MessageType> stack = new Stack<>();
    int[] numbers = new int[10];
    int[] lengths = new int[10];
    int[] indices = new int[10];
    int depth;
    int ptr;
    int string_ptr;

    @Override
    public void visitVarInt32(Field field, int v) {
      lengths[depth] += 1 + ProtoEncoder.computeRawVarint32Size(v);
    }

    @Override
    public void visitDouble(Field field, double d) {
      lengths[depth] += 1 + 8;
    }

    @Override
    public void visitString(Field field, String s) {
      byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
      state.strings[string_ptr++] = bytes;
      int l = bytes.length;
      lengths[depth] += 1 + ProtoEncoder.computeRawVarint32Size(l) + l;
    }

    @Override
    public void init(MessageType type) {
      if (!stack.isEmpty()) {
        throw new IllegalStateException();
      }
      string_ptr = 0;
      depth = 0;
      ptr = 0;
      stack.add(type);
      indices[0] = ptr++;
    }

    @Override
    public void enter(Field field) {
      stack.add((MessageType) field.type);
      numbers[depth] = field.number;
      depth++;
      indices[depth] = ptr++;
      lengths[depth] = 0;
    }

    @Override
    public void leave(Field field) {
      int l = lengths[depth];
      lengths[depth] = 0;
      state.capture[indices[depth]] = l;
      l += 1 + ProtoEncoder.computeRawVarint32Size(l);
      stack.pop();
      depth--;
      lengths[depth] += l;
    }

    @Override
    public void destroy() {
    }
  }

  static class EncodingPhase implements Visitor {

    State state;
    ProtoEncoder encoder;
    int ptr_;
    Stack<MessageType> stack = new Stack<>();
    int string_ptr;

    @Override
    public void init(MessageType type) {
      stack.add(type);
      ptr_ = 0;
      state.buffer = Buffer.buffer(state.capture[ptr_++]);
      encoder = new ProtoEncoder(state.buffer);
    }

    @Override
    public void visitVarInt32(Field field, int v) {
      encoder.writeTag(field.number, WireType.VARINT.id);
      encoder.writeVarInt32(v);
    }

    @Override
    public void visitDouble(Field field, double d) {
      encoder.writeTag(field.number, WireType.I64.id);
      encoder.writeDouble(d);
    }

    @Override
    public void visitString(Field field, String s) {
      byte[] data = state.strings[string_ptr++];
      encoder.writeTag(field.number, WireType.LEN.id);
      encoder.writeVarInt32(data.length);
      encoder.writeBytes(data);
    }

    @Override
    public void enter(Field field) {
      encoder.writeTag(field.number, field.type.wireType().id);
      encoder.writeVarInt32(state.capture[ptr_++]);
      stack.add((MessageType) field.type);
    }

    @Override
    public void leave(Field field) {
      stack.pop();
    }

    @Override
    public void destroy() {
      stack.pop();
    }
  }
}
