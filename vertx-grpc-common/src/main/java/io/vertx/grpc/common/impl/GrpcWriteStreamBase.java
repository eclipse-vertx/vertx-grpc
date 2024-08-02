package io.vertx.grpc.common.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.*;

import static io.vertx.grpc.common.GrpcError.mapHttp2ErrorCode;

public abstract class GrpcWriteStreamBase<S extends GrpcWriteStreamBase<S, T>, T> implements GrpcWriteStream<T> {

  protected final ContextInternal context;
  private final GrpcMessageEncoder<T> messageEncoder;
  private final WriteStream<Buffer> writeStream;

  protected String encoding;
  private boolean headersSent;
  private boolean trailersSent;
  private GrpcError error;

  private MultiMap headers;
  private MultiMap trailers;

  private Handler<Throwable> exceptionHandler;
  private Handler<GrpcError> errorHandler;

  public GrpcWriteStreamBase(ContextInternal context, WriteStream<Buffer> writeStream, GrpcMessageEncoder<T> messageEncoder) {
    this.context = context;
    this.writeStream = writeStream;
    this.messageEncoder = messageEncoder;
  }

  public void init() {
    writeStream.exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        GrpcError error = mapHttp2ErrorCode(reset.getCode());
        handleError(error);
      } else {
        handleException(err);
      }
    });
  }

  public S errorHandler(Handler<GrpcError> handler) {
    this.errorHandler = handler;
    return (S) this;
  }

  public void handleError(GrpcError error) {
    if (this.error == null) {
      this.error = error;
      Handler<GrpcError> handler = errorHandler;
      if (handler != null) {
        handler.handle(error);
      }
    }
  }

  private void handleException(Throwable err) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(err);
    }
  }

  public final ContextInternal context() {
    return context;
  }

  public boolean isHeadersSent() {
    return headersSent;
  }

  public boolean isTrailersSent() {
    return trailersSent;
  }

  public boolean isCancelled() {
    return error == GrpcError.CANCELLED;
  }

  @Override
  public final MultiMap headers() {
    if (headersSent) {
      throw new IllegalStateException("Headers already sent");
    }
    if (headers == null) {
      headers = MultiMap.caseInsensitiveMultiMap();
    }
    return headers;
  }

  public final MultiMap trailers() {
    if (trailersSent) {
      throw new IllegalStateException("Trailers already sent");
    }
    if (trailers == null) {
      trailers = MultiMap.caseInsensitiveMultiMap();
    }
    return trailers;
  }

  @Override
  public final boolean writeQueueFull() {
    return writeStream.writeQueueFull();
  }

  @Override
  public final S drainHandler(Handler<Void> handler) {
    writeStream.drainHandler(handler);
    return (S) this;
  }

  @Override
  public final S exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return (S) this;
  }

  @Override
  public S setWriteQueueMaxSize(int maxSize) {
    writeStream.setWriteQueueMaxSize(maxSize);
    return (S) this;
  }

  @Override
  public final Future<Void> write(T message) {
    return writeMessage(messageEncoder.encode(message));
  }

  @Override
  public final Future<Void> end(T message) {
    return endMessage(messageEncoder.encode(message));
  }

  @Override
  public final Future<Void> writeMessage(GrpcMessage data) {
    return writeMessage(data, false);
  }

  @Override
  public final Future<Void> endMessage(GrpcMessage message) {
    return writeMessage(message, true);
  }

  public final Future<Void> end() {
    return writeMessage(null, true);
  }

  protected abstract void sendHeaders(MultiMap headers, boolean end);
  protected abstract void sendTrailers(MultiMap trailers);
  protected abstract Future<Void> sendMessage(GrpcMessage message);
  protected abstract Future<Void> sendEnd();

  private Future<Void> writeMessage(GrpcMessage message, boolean end) {
    if (error != null) {
      throw new IllegalStateException("The stream is failed: " + error);
    }
    if (trailersSent) {
      throw new IllegalStateException("The stream has been closed");
    }
    if (message == null && !end) {
      throw new IllegalStateException();
    }
    if (encoding != null && message != null && !encoding.equals(message.encoding())) {
      switch (encoding) {
        case "gzip":
          message = GrpcMessageEncoder.GZIP.encode(message.payload());
          break;
        case "identity":
          if (!message.encoding().equals("identity")) {
            if (!message.encoding().equals("gzip")) {
              return Future.failedFuture("Encoding " + message.encoding() + " is not supported");
            }
            Buffer decoded;
            try {
              decoded = GrpcMessageDecoder.GZIP.decode(message);
            } catch (CodecException e) {
              return Future.failedFuture(e);
            }
            message = GrpcMessage.message("identity", decoded);
          }
          break;
      }
    }
    if (!headersSent) {
      headersSent = true;
      sendHeaders(headers, end);
    }
    if (end) {
      if (!trailersSent) {
        trailersSent = true;
      }
      if (message != null) {
        sendMessage(message);
      }
      sendTrailers(trailers);
      return sendEnd();
    } else {
      return sendMessage(message);
    }
  }
}
