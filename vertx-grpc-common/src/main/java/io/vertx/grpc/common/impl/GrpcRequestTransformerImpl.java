package io.vertx.grpc.common.impl;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GrpcRequestTransformerImpl implements GrpcRequestTransformer {

  private final String encoding;
  private final Map<String, GrpcCompressor> compressors;
  private final Map<String, GrpcDecompressor> decompressors;

  public GrpcRequestTransformerImpl(String encoding, Set<GrpcCompressor> compressors, Set<GrpcDecompressor> decompressors) {
    this.encoding = encoding;
    this.compressors = compressors.stream().collect(Collectors.toMap(GrpcCompressor::encoding, compressor -> compressor));
    this.decompressors = decompressors.stream().collect(Collectors.toMap(GrpcDecompressor::encoding, decompressor -> decompressor));
  }

  @Override
  public Future<GrpcMessage> apply(GrpcMessage message) {
    Buffer payload;
    if (message != null) {
      if (encoding != null) {
        if (message.encoding().equals(encoding)) {
          payload = message.payload();
        } else if (message.encoding().equals("identity")) {
          // Message is in identity encoding, need to compress
          GrpcCompressor compressor = compressors.get(encoding);
          if (compressor == null) {
            return Future.failedFuture("Encoding " + encoding + " is not supported");
          }
          try {
            payload = compressor.compress(message.payload());
          } catch (CodecException e) {
            return Future.failedFuture(e);
          }
        } else {
          // Message is in some other encoding, need to decompress first then compress
          GrpcDecompressor decompressor = decompressors.get(message.encoding());
          if (decompressor == null) {
            return Future.failedFuture("Encoding " + message.encoding() + " is not supported");
          }

          Buffer decompressed;
          try {
            decompressed = decompressor.decompress(message.payload());
          } catch (CodecException e) {
            return Future.failedFuture(e);
          }

          if (encoding.equals("identity")) {
            payload = decompressed;
          } else {
            GrpcCompressor compressor = compressors.get(encoding);
            if (compressor == null) {
              return Future.failedFuture("Encoding " + encoding + " is not supported");
            }
            try {
              payload = compressor.compress(decompressed);
            } catch (CodecException e) {
              return Future.failedFuture(e);
            }
          }
        }
      } else {
        payload = message.payload();
      }
    } else {
      payload = null;
    }

    // Create the transformed message with the appropriate encoding
    String messageEncoding = encoding != null ? encoding : (message != null ? message.encoding() : "identity");
    GrpcMessage msg = GrpcMessage.message(messageEncoding, message.format() != null ? message.format() : WireFormat.PROTOBUF, payload);

    return Future.succeededFuture(msg);
  }
}
