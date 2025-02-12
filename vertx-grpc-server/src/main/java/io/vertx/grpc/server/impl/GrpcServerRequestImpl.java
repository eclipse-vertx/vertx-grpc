/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.server.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Timer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.common.impl.GrpcReadStreamBase;
import io.vertx.grpc.common.impl.GrpcWriteStreamBase;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.transcoding.HttpVariableBinding;
import io.vertx.grpc.transcoding.MessageWeaver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerRequestImpl<Req, Resp> extends GrpcReadStreamBase<GrpcServerRequestImpl<Req, Resp>, Req> implements GrpcServerRequest<Req, Resp> {

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

  private static long parseTimeout(String timeout) {
    Matcher matcher = TIMEOUT_PATTERN.matcher(timeout);
    if (matcher.matches()) {
      long value = Long.parseLong(matcher.group(1));
      TimeUnit unit = TIMEOUT_MAPPING.get(matcher.group(2));
      return unit.toMillis(value);
    } else {
      return 0L;
    }
  }

  private static final BufferInternal EMPTY_BUFFER = BufferInternal.buffer(Unpooled.EMPTY_BUFFER);

  final HttpServerRequest httpRequest;
  final String transcodingRequestBody;
  final List<HttpVariableBinding> bindings;
  final long timeout;
  final boolean scheduleDeadline;
  final GrpcProtocol protocol;
  private GrpcServerResponseImpl<Req, Resp> response;
  private final GrpcMethodCall methodCall;
  private BufferInternal grpcWebTextBuffer;
  private Timer deadline;

  public GrpcServerRequestImpl(io.vertx.core.internal.ContextInternal context,
                               boolean scheduleDeadline,
                               GrpcProtocol protocol,
                               WireFormat format,
                               long maxMessageSize,
                               HttpServerRequest httpRequest,
                               String transcodingRequestBody,
                               String transcodingResponseBody,
                               List<HttpVariableBinding> bindings,
                               GrpcMessageDecoder<Req> messageDecoder,
                               GrpcMessageEncoder<Resp> messageEncoder,
                               GrpcMethodCall methodCall) {
    super(context, httpRequest, httpRequest.headers().get("grpc-encoding"), format, GrpcServerRequestImpl.isTranscodable(httpRequest), maxMessageSize, messageDecoder);
    String timeoutHeader = httpRequest.getHeader("grpc-timeout");
    long timeout = timeoutHeader != null ? parseTimeout(timeoutHeader) : 0L;

    this.protocol = protocol;
    this.timeout = timeout;
    this.httpRequest = httpRequest;
    this.transcodingRequestBody = transcodingRequestBody;
    this.bindings = bindings;
    this.methodCall = methodCall;
    this.scheduleDeadline = scheduleDeadline;
    if (httpRequest.version() != HttpVersion.HTTP_2 && GrpcMediaType.isGrpcWebText(httpRequest.getHeader(CONTENT_TYPE))) {
      grpcWebTextBuffer = EMPTY_BUFFER;
    } else {
      grpcWebTextBuffer = null;
    }
  }

  @Override
  public void init(GrpcWriteStreamBase ws) {
    this.response = (GrpcServerResponseImpl<Req, Resp>) ws;
    super.init(ws);
    if (timeout > 0L) {
      if (scheduleDeadline) {
        Timer timer = context.timer(timeout, TimeUnit.MILLISECONDS);
        deadline = timer;
        timer.onSuccess(v -> {
          response.handleTimeout();
        });
      }
    }
  }

  public static boolean isTranscodable(HttpServerRequest httpRequest) {
    if (httpRequest == null) {
      return false;
    }

    return (httpRequest.version() == HttpVersion.HTTP_1_0 ||
      httpRequest.version() == HttpVersion.HTTP_1_1) &&
      GrpcProtocol.HTTP_1.mediaType().equals(httpRequest.getHeader(CONTENT_TYPE));
  }

  void cancelTimeout() {
    Timer timer = deadline;
    if (timer != null) {
      deadline = null;
      timer.cancel();
    }
  }

  public String fullMethodName() {
    return methodCall.fullMethodName();
  }

  @Override
  public MultiMap headers() {
    return httpRequest.headers();
  }

  @Override
  public ServiceName serviceName() {
    return methodCall.serviceName();
  }

  @Override
  public String methodName() {
    return methodCall.methodName();
  }

  @Override
  public GrpcServerRequestImpl<Req, Resp> handler(Handler<Req> handler) {
    if (handler != null) {
      return messageHandler(msg -> {
        Req decoded;
        try {
          decoded = decodeMessage(msg);
        } catch (CodecException e) {
          response.cancel();
          return;
        }
        handler.handle(decoded);
      });
    } else {
      return messageHandler(null);
    }
  }

  public GrpcServerResponse<Req, Resp> response() {
    return response;
  }

  @Override
  public HttpConnection connection() {
    return httpRequest.connection();
  }

  @Override
  public long timeout() {
    return timeout;
  }

  @Override
  public Timer deadline() {
    return deadline;
  }

  @Override
  public void handle(Buffer chunk) {
    if (notGrpcWebText()) {
      if (isTranscodable(httpRequest)) {
        try {
          BufferInternal transcoded = (BufferInternal) MessageWeaver.weaveRequestMessage(chunk, bindings, transcodingRequestBody);
          if (transcoded == null) {
            return;
          }

          Buffer prefixed = BufferInternal.buffer(transcoded.length() + 5);

          prefixed.appendByte((byte) 0); // uncompressed flag
          prefixed.appendInt(transcoded.length()); // content length
          prefixed.appendBuffer(transcoded);

          chunk = prefixed;
        } catch (DecodeException e) {
          cancelTranscodable();
          httpRequest.response().setStatusCode(400);
          if (!httpRequest.response().ended()) {
            response.end();
          }

          return;
        }
      }

      super.handle(chunk);
      return;
    }
    if (grpcWebTextBuffer == EMPTY_BUFFER) {
      ByteBuf bbuf = ((BufferInternal) chunk).getByteBuf();
      if ((chunk.length() & 0b11) == 0) {
        // Content length is divisible by four, so we decode it immediately
        super.handle(BufferInternal.buffer(Base64.decode(bbuf)));
      } else {
        grpcWebTextBuffer = BufferInternal.buffer(bbuf.copy());
      }
      return;
    }
    bufferAndDecode(chunk);
  }

  private boolean notGrpcWebText() {
    return grpcWebTextBuffer == null;
  }

  private void bufferAndDecode(Buffer chunk) {
    grpcWebTextBuffer.appendBuffer(chunk);
    int len = grpcWebTextBuffer.length();
    // Decode base64 content as soon as we have more bytes than a multiple of four.
    // We could instead wait for the buffer length to be a multiple of four,
    // But then in the worst case we may have to buffer the whole request.
    int maxDecodable = len & ~0b11;
    if (maxDecodable == len) {
      BufferInternal decoded = BufferInternal.buffer(Base64.decode(grpcWebTextBuffer.getByteBuf()));
      grpcWebTextBuffer = EMPTY_BUFFER;
      super.handle(decoded);
    } else if (maxDecodable > 0) {
      ByteBuf bbuf = grpcWebTextBuffer.getByteBuf();
      BufferInternal decoded = BufferInternal.buffer(Base64.decode(bbuf, 0, maxDecodable));
      grpcWebTextBuffer = BufferInternal.buffer(bbuf.copy(maxDecodable, len - maxDecodable));
      super.handle(decoded);
    }
  }
}
