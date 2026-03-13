package io.vertx.grpc.common.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.*;

import java.util.Objects;

import static io.vertx.grpc.common.GrpcError.mapHttp2ErrorCode;

public abstract class GrpcWriteStreamBase<S extends GrpcWriteStreamBase<S, T>, T> implements GrpcWriteStream<T> {

  protected final ContextInternal context;
  private final GrpcMessageEncoder<T> messageEncoder;
  private final WriteStream<?> writeStream;

  protected String mediaType;
  protected String encoding;
  protected WireFormat format;
  private boolean headersSent;
  private boolean trailersSent;
  private GrpcError error;
  private boolean cancelled;

  private MultiMap headers;
  private MultiMap trailers;

  private Handler<Throwable> exceptionHandler;
  private Handler<GrpcError> errorHandler;

  public GrpcWriteStreamBase(ContextInternal context, String mediaType, WriteStream<?> writeStream, GrpcMessageEncoder<T> messageEncoder) {
    this.context = context;
    this.writeStream = writeStream;
    this.messageEncoder = messageEncoder;
    this.mediaType = mediaType;
  }

  public void init() {
    writeStream.exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        GrpcError error = mapHttp2ErrorCode(reset.getCode());
        handleError(error);
      }
      handleException(err);
    });
  }

  public S errorHandler(Handler<GrpcError> handler) {
    this.errorHandler = handler;
    return (S) this;
  }

  public void handleError(GrpcError error) {
    if (this.error == null) {
      cancelled |= error == GrpcError.CANCELLED;
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

  public void handleStatus(GrpcStatus status) {
    cancelled |= status == GrpcStatus.CANCELLED;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void cancel() {
    if (!cancelled) {
      cancelled = sendCancel();
    }
  }

  @Override
  public final S encoding(String encoding) {
    if (headersSent) {
      throw new IllegalStateException("Cannot set encoding when headers have been sent");
    }
    this.encoding = Objects.requireNonNull(encoding);
    return (S) this;
  }

  @Override
  public final S format(WireFormat format) {
    if (headersSent) {
      throw new IllegalStateException("Cannot set format when headers have been sent");
    }
    this.format = Objects.requireNonNull(format);
    return (S) this;
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
    return writeMessage(encodeMessage(message));
  }

  @Override
  public final Future<Void> end(T message) {
    return endMessage(encodeMessage(message));
  }

  private GrpcMessage encodeMessage(T message) {
    WireFormat f = format;
    if (f == null) {
      f = WireFormat.PROTOBUF;
    }
    return messageEncoder.encode(message, f);
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

  protected abstract void setTrailers(String contentType, String encoding, MultiMap headers, MultiMap trailers);
  protected abstract void setTrailers(MultiMap trailers);

  protected abstract Future<Void> sendHead(String contentType, String encoding, MultiMap headers);
  protected abstract Future<Void> sendMessage(GrpcMessage message);
  protected abstract Future<Void> sendEnd();
  protected abstract boolean sendCancel();

  protected Future<Void> sendHead(boolean writeHeaders) {
    if (!writeHeaders) {
      throw new IllegalArgumentException();
    }
    String contentType = contentType(format);
    return sendHead(contentType, encoding, headers);
  }

  protected Future<Void> sendMessage(boolean writeHeaders, GrpcMessage message) {
    if (writeHeaders) {
      String contentType = contentType(format);
      sendHead(contentType, encoding, headers);
    }
    return sendMessage(message);
  }

  protected Future<Void> sendEnd(boolean writeHeaders) {
    if (writeHeaders) {
      String contentType = contentType(format);
      setTrailers(contentType, encoding, headers, trailers);
    } else {
      setTrailers(trailers);
    }
    return sendEnd();
  }

  protected String contentType(WireFormat wireFormat) {
    if (wireFormat != null) {
      switch (wireFormat) {
        case JSON:
          if (!mediaType.endsWith("/json")) {
            return mediaType + "+json";
          }
        case PROTOBUF:
          // contentType = mediaType + "+proto";
          break;
      }
    }
    return mediaType;
  }

  public final Future<Void> writeHead() {
    return writeMessage(null, false);
  }

  protected Future<Void> writeMessage(GrpcMessage message, boolean end) {
    if (error != null) {
      throw new IllegalStateException("The stream is failed: " + error);
    }
    if (trailersSent) {
      throw new IllegalStateException("The stream has been closed");
    }
    if (message != null) {
      if (format == null) {
        format = message.format();
      } else if (!format.equals(message.format())) {
        return context.failedFuture("Message format does not match the response format");
      }
    }
    GrpcMessage payload;
    if (message != null) {
      if (encoding != null) {
        switch (encoding) {
          case "gzip":
            if (message.encoding().equals("identity")) {
              payload = new GrpcTransformedMessage(message, "gzip", Utils.GZIP_ENCODER);
            } else {
              if (!message.encoding().equals("gzip")) {
                return Future.failedFuture("Encoding " + message.encoding() + " is not supported");
              }
              payload = message;
            }
            break;
          case "identity":
            if (!message.encoding().equals("identity")) {
              if (!message.encoding().equals("gzip")) {
                return Future.failedFuture("Encoding " + message.encoding() + " is not supported");
              }
              payload = new GrpcTransformedMessage(message, "identity", Utils.GZIP_DECODER);
            } else {
              payload = message;
            }
            break;
          default:
            return Future.failedFuture("Encoding " + encoding + " is not supported");
        }
      } else {
        payload = message;
      }
    } else {
      payload = null;
    }

    boolean writeHeaders;
    if (!headersSent) {
      headersSent = true;
      writeHeaders = true;
    } else {
      writeHeaders = false;
      // That should not happen
      if (payload == null && !end) {
        throw new IllegalStateException();
      }
    }
    if (end) {
      trailersSent = true;
      if (payload != null) {
        sendMessage(writeHeaders, payload);
        writeHeaders = false;
      }
      return sendEnd(writeHeaders);
    } else {
      if (payload != null) {
        return sendMessage(writeHeaders, payload);
      } else {
        return sendHead(writeHeaders);
      }
    }
  }
}
