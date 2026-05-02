package io.vertx.grpc.common.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;

import java.util.Objects;

public abstract class GrpcWriteStreamBase<S extends GrpcWriteStreamBase<S, T>, T> implements GrpcWriteStream<T> {

  protected final ContextInternal context;
  private final GrpcMessageEncoder<T> messageEncoder;

  protected String encoding;
  protected WireFormat format;
  private boolean headersSent;
  private boolean trailersSent;
  private GrpcError error;
  private boolean cancelled;
  private MultiMap headers;
  private MultiMap trailers;
  private Handler<Throwable> exceptionHandler;

  public GrpcWriteStreamBase(ContextInternal context, GrpcMessageEncoder<T> messageEncoder) {
    this.context = context;
    this.messageEncoder = messageEncoder;
    this.format = null;
  }

  public void handleError(GrpcError error) {
    if (this.error == null) {
      this.error = error;
    }
  }

  public void handleCancel() {
    cancelled = true;
  }

  public void handleException(Throwable err) {
    if (err instanceof GrpcErrorException) {
      GrpcErrorException ee = (GrpcErrorException) err;
      handleError(ee.error());
    }
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
  public final S exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
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
      f = messageEncoder.accepts(WireFormat.PROTOBUF) ? WireFormat.PROTOBUF : WireFormat.JSON;
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

  protected abstract Future<Void> sendTrailers(MultiMap trailers);
  protected abstract Future<Void> sendHeaders(WireFormat wireFormat, String encoding, MultiMap headers);
  protected abstract Future<Void> sendMessage(GrpcMessage message);
  protected abstract boolean sendCancel();

  private Future<Void> sendHeaders(boolean writeHeaders) {
    if (!writeHeaders) {
      throw new IllegalArgumentException();
    }
    return sendHeaders(format, encoding, headers);
  }

  private Future<Void> sendMessage(boolean writeHeaders, GrpcMessage message) {
    if (writeHeaders) {
      sendHeaders(format, encoding, headers);
    }
    return sendMessage(message);
  }

  private Future<Void> sendEnd() {
    return sendTrailers(trailers);
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
      }
      return sendEnd();
    } else {
      if (payload != null) {
        return sendMessage(writeHeaders, payload);
      } else {
        return sendHeaders(writeHeaders);
      }
    }
  }
}
