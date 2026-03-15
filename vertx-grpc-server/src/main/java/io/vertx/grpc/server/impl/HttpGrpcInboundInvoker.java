package io.vertx.grpc.server.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.DefaultGrpcCancelFrame;
import io.vertx.grpc.common.GrpcCancelFrame;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcErrorException;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.DefaultGrpcHeadersFrame;
import io.vertx.grpc.common.impl.DefaultGrpcMessageFrame;
import io.vertx.grpc.common.impl.GrpcDeframingStream;
import io.vertx.grpc.common.impl.GrpcFrame;
import io.vertx.grpc.common.impl.GrpcHeadersFrame;
import io.vertx.grpc.common.impl.GrpcInboundInvoker;
import io.vertx.grpc.common.impl.GrpcMessageDeframer;
import io.vertx.grpc.server.GrpcProtocol;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpGrpcInboundInvoker implements GrpcInboundInvoker {

  private static final Pattern TIMEOUT_PATTERN = Pattern.compile("([0-9]{1,8})([HMSmun])");

  private static final Map<String, TimeUnit> TIMEOUT_MAPPING;

  static {
    Map<String, TimeUnit> timeoutMapping = new HashMap<>();
    timeoutMapping.put("H", TimeUnit.HOURS);
    timeoutMapping.put("M", TimeUnit.MINUTES);
    timeoutMapping.put("S", TimeUnit.SECONDS);
    timeoutMapping.put("m", TimeUnit.MILLISECONDS);
    timeoutMapping.put("u", TimeUnit.MICROSECONDS);
    timeoutMapping.put("n", TimeUnit.NANOSECONDS);
    TIMEOUT_MAPPING = timeoutMapping;
  }

  protected final GrpcProtocol protocol;
  protected final ContextInternal context;
  private final GrpcMessageDeframer deframer;
  private GrpcDeframingStream deframingStream;
  private Handler<GrpcFrame> frameHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;

  public HttpGrpcInboundInvoker(ContextInternal context, GrpcProtocol protocol, GrpcMessageDeframer deframer) {
    this.context = context;
    this.deframer = deframer;
    this.protocol = protocol;
  }

  @Override
  public GrpcInboundInvoker handler(Handler<GrpcFrame> handler) {
    this.frameHandler = handler;
    return this;
  }

  @Override
  public GrpcInboundInvoker exceptionHandler(Handler<Throwable> handler) {
    if (handler != null && exceptionHandler != null) {
      throw new IllegalStateException();
    }
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public GrpcInboundInvoker endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  void handleException(Throwable err) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        GrpcErrorException grpcError = GrpcErrorException.create(reset);
        if (grpcError.error() == GrpcError.CANCELLED) {
          GrpcCancelFrame frame = DefaultGrpcCancelFrame.INSTANCE;
          emit(frame);
        }
        err = grpcError;
      }
      handler.handle(err);
    }
  }

  void init(HttpServerRequest httpRequest, long maxMessageSize) {

    // Wire
    GrpcDeframingStream stream = new GrpcDeframingStream(context, httpRequest, deframer);
    stream.handler(message -> {
      emit(new DefaultGrpcMessageFrame(message));
    });

    stream.exceptionHandler(this::handleException);

    stream.endHandler(v -> {
      Handler<Void> handler = endHandler;
      if (handler != null) {
        handler.handle(null);
      }
    });

    stream.init(maxMessageSize);

    deframingStream = stream;

    String timeoutHeader = httpRequest.getHeader(GrpcHeaderNames.GRPC_TIMEOUT);
    Duration timeout = timeoutHeader != null ? parseTimeout(timeoutHeader) : null;

    // Fire GrpcHeadersFrame event
    String encoding = httpRequest.headers().get(GrpcHeaderNames.GRPC_ENCODING);
    String contentType = httpRequest.headers().get(HttpHeaders.CONTENT_TYPE);

    WireFormat wireFormat = GrpcMediaType.parseContentType(contentType, protocol.mediaType());

    GrpcHeadersFrame headersFrame = new DefaultGrpcHeadersFrame(wireFormat, encoding, httpRequest.headers(), timeout);

    emit(headersFrame);
  }

  private void emit(GrpcFrame frame) {
    Handler<GrpcFrame> handler = frameHandler;
    if (handler != null) {
      handler.handle(frame);
    }
  }

  @Override
  public GrpcInboundInvoker pause() {
    deframingStream.pause();
    return this;
  }

  @Override
  public GrpcInboundInvoker resume() {
    deframingStream.resume();
    return this;
  }

  @Override
  public GrpcInboundInvoker fetch(long amount) {
    deframingStream.fetch(amount);
    return this;
  }

  private static Duration parseTimeout(String timeout) {
    Matcher matcher = TIMEOUT_PATTERN.matcher(timeout);
    if (matcher.matches()) {
      long value = Long.parseLong(matcher.group(1));
      TimeUnit unit = TIMEOUT_MAPPING.get(matcher.group(2));
      return Duration.of(value, unit.toChronoUnit());
    } else {
      return null;
    }
  }
}
