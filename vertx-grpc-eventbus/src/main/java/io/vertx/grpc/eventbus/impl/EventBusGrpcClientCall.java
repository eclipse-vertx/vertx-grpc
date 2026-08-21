package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.eventbus.transport.v1alpha.*;

import java.time.Duration;

class EventBusGrpcClientCall extends EventBusGrpcStreamBase<EventBusGrpcClientEndpoint> {

  private final ServiceName serviceName;
  private final String methodName;

  private WireFormat wireFormat;
  private String encoding;
  private MultiMap requestHeaders;
  private Duration timeout;
  private Future<Void> halfCloseWritten;
  private State state;
  private final Outbound outbound;

  public EventBusGrpcClientCall(EventBusGrpcClientEndpoint localEndpoint, long id, ContextInternal context, boolean localUnary, boolean remoteUnary,
                                ServiceName serviceName, String methodName, int initialInboundWindowSize, int initialOutboundWindowSize) {
    super(localEndpoint, id, context, localUnary, remoteUnary, initialInboundWindowSize, initialOutboundWindowSize);
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.state = State.IDLE;
    this.outbound = localUnary ? new UnaryOutbound() : new StreamingOutbound();
    this.encoding = "identity";
    this.wireFormat = WireFormat.PROTOBUF;
  }

  @Override
  WireFormat format() {
    return wireFormat;
  }

  @Override
  String encoding() {
    return encoding;
  }

  private interface Outbound {
    Future<Void> write(GrpcFrame frame);
  }

  private class UnaryOutbound implements Outbound {

    private GrpcMessage message;
    private Promise<Void> halfClosePromise;

    @Override
    public Future<Void> write(GrpcFrame frame) {
      if (halfCloseWritten != null) {
        return consumerContext.failedFuture("Stream closed");
      }
      switch (frame.type()) {
        case HEADERS:
          GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
          if (headersFrame.format() != null) {
            wireFormat = headersFrame.format();
          }
          if (headersFrame.encoding() != null) {
            encoding = headersFrame.encoding();
          }
          requestHeaders = headersFrame.headers();
          timeout = headersFrame.timeout();
          return consumerContext.succeededFuture();
        case MESSAGE:
          if (message != null) {
            return consumerContext.failedFuture("A message frame has already been written");
          }
          message = ((GrpcMessageFrame) frame).message();
          halfClosePromise = consumerContext.promise();
          return consumerContext.succeededFuture();
        case HALF_CLOSE:
          GrpcMessage msg = message;
          if (msg == null) {
            return consumerContext.failedFuture("A message frame must have been sent prior closing the stream");
          }
          Future<Void> fut = connect(msg);
          fut.onComplete(halfClosePromise);
          halfCloseWritten = fut;
          return fut;
        default:
          return consumerContext.failedFuture("Frame not handled");
      }
    }

    private Future<Void> connect(GrpcMessage message) {
      Buffer payload = message != null ? message.payload() : Buffer.buffer();
      Object body = EventBusGrpcCodec.encodeBody(payload, wireFormat);
      return EventBusGrpcClientCall.this.connect(body);
    }
  }

  private class StreamingOutbound implements Outbound {
    @Override
    public Future<Void> write(GrpcFrame frame) {
      switch (frame.type()) {
        case HEADERS:
          GrpcHeadersFrame headersFrame = (GrpcHeadersFrame) frame;
          if (headersFrame.format() != null) {
            wireFormat = headersFrame.format();
          }
          if (headersFrame.encoding() != null) {
            encoding = headersFrame.encoding();
          }
          requestHeaders = headersFrame.headers();
          timeout = headersFrame.timeout();
          state = State.CONNECTING;
          return connect();
        case MESSAGE:
          return writeMessage(frame);
        case HALF_CLOSE:
          Future<Void> res = writeMessage(frame);
          halfCloseWritten = res;
          return res;
        default:
          return consumerContext.failedFuture("Invalid message: " + frame.type());
      }
    }

    private Future<Void> connect() {
      return EventBusGrpcClientCall.this.connect(null);
    }
  }

  @Override
  public Future<Void> write(GrpcFrame frame) {
    if (state == State.CLOSED) {
      return consumerContext.failedFuture("Stream closed");
    }
    if (frame.type() == GrpcFrameType.CANCEL) {
      return writeCancel();
    } else {
      return outbound.write(frame);
    }
  }

  @Override
  public Future<Void> end(GrpcFrame frame) {
    if (state == State.CLOSED) {
      return consumerContext.failedFuture("Stream closed");
    }
    return write(frame).compose(v -> end());
  }

  @Override
  public Future<Void> end() {
    State s = state;
    switch (s) {
      case CONNECTING:
        return consumerContext.failedFuture("Stream opening");
      case STREAMING:
        Future<Void> ret = halfCloseWritten;
        if (ret == null) {
          return consumerContext.failedFuture("An half-close frame must be sent prior closing the stream");
        }
      default:
        return consumerContext.failedFuture(new IllegalStateException("Stream closed"));
    }
  }

  private Future<Void> connect(Object body) {
    Future<Void> res = connect(body, requestHeaders, serviceName, methodName, localEndpoint.pingTimeout, encoding, wireFormat, timeout);
    if (!remoteUnary) {
      res = res.andThen(ar -> {
        if (ar.succeeded()) {
          EventBusGrpcClientCall.this.state = State.STREAMING;
        }
      });
    }
    return res;
  }

  private Future<Void> writeCancel() {
    if (state == State.STREAMING) {
      return sendTransportFrame(TransportFrame.newBuilder().setCancel(Cancel.newBuilder().setStatus(GrpcStatus.CANCELLED.code)), null);
    } else {
      return consumerContext.failedFuture("Not supported");
    }
  }

  private Future<Void> writeMessage(GrpcFrame frame) {
    State s = state;
    switch (s) {
      case CONNECTING:
      case STREAMING:
        return enqueue(frame);
      default:
        return consumerContext.failedFuture(new IllegalStateException("Stream closed"));
    }
  }

  @Override
  void handleClosed() {
    assert state != State.CLOSED;
    state = State.CLOSED;
  }

  private enum State {
    IDLE,
    CONNECTING,
    STREAMING,
    CLOSED
  }
}
