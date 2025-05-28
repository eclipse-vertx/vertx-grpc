package io.vertx.grpc.common.impl;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.*;

public class GrpcRequestTransformerImpl implements GrpcRequestTransformer {

  private final String encoding;
  private final ReadStream<GrpcMessage> source;

  private boolean endOnFailure = false;
  private boolean endOnSuccess = true;
  private boolean endOnComplete = true;
  private WriteStream<GrpcMessage> destination;

  public GrpcRequestTransformerImpl(ReadStream<GrpcMessage> source, String encoding) {
    this.source = source;
    this.encoding = encoding;
  }

  @Override
  public Pipe<GrpcMessage> endOnFailure(boolean b) {
    this.endOnFailure = b;
    return this;
  }

  @Override
  public Pipe<GrpcMessage> endOnSuccess(boolean b) {
    this.endOnSuccess = b;
    return this;
  }

  @Override
  public Pipe<GrpcMessage> endOnComplete(boolean b) {
    this.endOnComplete = b;
    return this;
  }

  @Override
  public Future<Void> to(WriteStream<GrpcMessage> writeStream) {
    this.destination = writeStream;
    return Future.future(promise -> {
      source.pause();

      // Set up handlers
      source.handler(message -> {
        // Transform and write the message
        Future<GrpcMessage> transformedFuture = transformMessage(message);

        transformedFuture.onComplete(ar -> {
          if (ar.succeeded()) {
            GrpcMessage transformed = ar.result();
            if (transformed != null) {
              destination.write(transformed);

              // Handle backpressure
              if (destination.writeQueueFull()) {
                source.pause();
                destination.drainHandler(v -> source.resume());
              }
            }
          } else {
            if (endOnFailure) {
              destination.end();
            }
            promise.fail(ar.cause());
          }
        });
      });

      source.endHandler(v -> {
        if (endOnComplete) {
          destination.end();
        }
        if (endOnSuccess) {
          promise.complete();
        }
      });

      source.exceptionHandler(err -> {
        if (endOnFailure) {
          destination.end();
        }
        promise.fail(err);
      });

      // Handle destination write queue
      destination.drainHandler(v -> source.resume());

      // Start reading
      source.resume();
    });
  }

  @Override
  public void close() {
    if (source != null) {
      source.pause();
      source.handler(null);
      source.endHandler(null);
      source.exceptionHandler(null);
    }
  }

  private Future<GrpcMessage> transformMessage(GrpcMessage message) {
    if (message == null) {
      return Future.succeededFuture(null);
    }

    return writeMessage(message);
  }

  private Future<GrpcMessage> writeMessage(GrpcMessage message) {
    Buffer payload;
    if (message != null) {
      if (encoding != null) {
        if (message.encoding().equals(encoding)) {
          payload = message.payload();
        } else if (message.encoding().equals("identity")) {
          // Message is in identity encoding, need to compress
          GrpcCompressor compressor = GrpcCompressor.lookupCompressor(encoding);
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
          GrpcDecompressor decompressor = GrpcDecompressor.lookupDecompressor(message.encoding());
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
            GrpcCompressor compressor = GrpcCompressor.lookupCompressor(encoding);
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

  // Message is already in the desired encoding
  //compressed = !encoding.equals("identity");
}
