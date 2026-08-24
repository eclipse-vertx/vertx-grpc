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

  private String encoding;
  private WireFormat format;
  private boolean headersWritten;
  private boolean endWritten;
  private GrpcError error;
  private Future<Void> cancellation;
  private MultiMap headers;
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
    cancellation = context.succeededFuture();
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
    if (cancellation == null && status == GrpcStatus.CANCELLED) {
      cancellation = context.succeededFuture();
    }
  }

  @Override
  public boolean isCancelled() {
    Future<Void> c = cancellation;
    // Compute an optimistic view of cancellation
    return c != null && (c.succeeded() || !c.isComplete());
  }

  @Override
  public void cancel() {
    if (cancellation == null) {
      cancellation = sendCancel();
    }
  }

  @Override
  public Future<Void> cancellation() {
    return cancellation;
  }

  @Override
  public final S encoding(String encoding) {
    if (headersWritten) {
      throw new IllegalStateException("Cannot set encoding when headers have been sent");
    }
    this.encoding = Objects.requireNonNull(encoding);
    return (S) this;
  }

  public final String encoding() {
    return encoding;
  }

  @Override
  public final S format(WireFormat format) {
    if (headersWritten) {
      throw new IllegalStateException("Cannot set format when headers have been sent");
    }
    this.format = Objects.requireNonNull(format);
    return (S) this;
  }

  public final WireFormat format() {
    return format;
  }

  public final ContextInternal context() {
    return context;
  }

  public boolean isHeadersWritten() {
    return headersWritten;
  }

  public boolean isEndWritten() {
    return endWritten;
  }

  @Override
  public final MultiMap headers() {
    if (headersWritten) {
      throw new IllegalStateException("Headers already sent");
    }
    if (headers == null) {
      headers = MultiMap.caseInsensitiveMultiMap();
    }
    return headers;
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

  protected abstract Future<Void> sendHead();
  protected abstract Future<Void> sendMessage(GrpcMessage message);
  protected abstract Future<Void> sendEnd();
  protected abstract Future<Void> sendCancel();

  protected Future<Void> sendEnd(GrpcMessage message) {
    sendMessage(message);
    return sendEnd();
  }

  private Future<Void> sendHead(boolean writeHeaders) {
    if (!writeHeaders) {
      throw new IllegalArgumentException();
    }
    return sendHead();
  }

  public final Future<Void> writeHead() {
    return writeMessage(null, false);
  }

  private Future<Void> writeMessage(GrpcMessage message, boolean end) {
    if (error != null) {
      return context.failedFuture(new GrpcErrorException(error, error.status));
    }
    if (end && endWritten) {
      throw new IllegalStateException("The stream is ended");
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
    if (!headersWritten) {
      writeHeaders = true;
    } else {
      writeHeaders = false;
      // That should not happen
      if (payload == null && !end) {
        throw new IllegalStateException();
      }
    }
    try {
      if (end) {
        endWritten = true;
        if (payload != null) {
          return sendEnd(payload);
        } else {
          return sendEnd();
        }
      } else {
        if (payload != null) {
          return sendMessage(payload);
        } else {
          return sendHead(writeHeaders);
        }
      }
    } finally {
      if (writeHeaders) {
        headersWritten = true;
      }
    }
  }
}
