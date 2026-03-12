package io.vertx.grpc.transcoding.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;

import java.util.List;

public class TranscodingMessageDecoder<Req> implements GrpcMessageDecoder<Req> {

  private final GrpcMessageDecoder<Req> messageDecoder;
  private final String transcodingRequestBody;
  private final List<HttpVariableBinding> bindings;

  public TranscodingMessageDecoder(GrpcMessageDecoder<Req> messageDecoder, String transcodingRequestBody, List<HttpVariableBinding> bindings) {
    this.messageDecoder = messageDecoder;
    this.transcodingRequestBody = transcodingRequestBody;
    this.bindings = bindings;
  }

  @Override
  public Req decode(GrpcMessage msg) throws CodecException {
    Buffer transcoded;
    try {
      transcoded = MessageWeaver.weaveRequestMessage(msg.payload(), bindings, transcodingRequestBody, messageDecoder.messageDescriptor());
    } catch (DecodeException e) {
      throw new CodecException(e);
    }
    return messageDecoder.decode(GrpcMessage.message("identity", WireFormat.JSON, transcoded));
  }
  @Override
  public boolean accepts(WireFormat format) {
    return messageDecoder.accepts(format);
  }
}
