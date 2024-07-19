package io.vertx.grpc.common;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.buffer.VertxByteBufAllocator;

import java.util.Queue;

@VertxGen
public interface GrpcMessageEncoder<T> {

  /**
   * Create an encoder for arbitrary message extending {@link MessageLite}.
   * @return the message encoder
   */
  @GenIgnore
  static <T extends MessageLite> GrpcMessageEncoder<T> encoder() {
    return msg -> {
      byte[] bytes = msg.toByteArray();
      return GrpcMessage.message("identity", Buffer.buffer(bytes));
    };
  }

  GrpcMessageEncoder<Buffer> IDENTITY = new GrpcMessageEncoder<Buffer>() {
    @Override
    public GrpcMessage encode(Buffer payload) {
      return GrpcMessage.message("identity", payload);
    }
  };

  GrpcMessageEncoder<Buffer> GZIP = new GrpcMessageEncoder<Buffer>() {
    @Override
    public GrpcMessage encode(Buffer payload) {
      CompositeByteBuf composite = Unpooled.compositeBuffer();
      GzipOptions options = StandardCompressionOptions.gzip();
      ZlibEncoder encoder = ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP, options.compressionLevel(), options.windowBits(), options.memLevel());
      EmbeddedChannel channel = new EmbeddedChannel(encoder);
      channel.config().setAllocator(VertxByteBufAllocator.UNPOOLED_ALLOCATOR);
      channel.writeOutbound(((BufferInternal) payload).getByteBuf());
      channel.finish();
      Queue<Object> messages = channel.outboundMessages();
      ByteBuf a;
      while ((a = (ByteBuf) messages.poll()) != null) {
        composite.addComponent(true, a);
      }
      channel.close();
      return GrpcMessage.message("gzip", BufferInternal.buffer(composite));
    }
  };

  GrpcMessage encode(T msg);

}
