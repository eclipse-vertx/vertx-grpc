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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.transcoding.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcServerImpl implements GrpcServer {

  private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("application/grpc(-web(-text)?)?(\\+(json|proto))?");

  private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

  private final GrpcServerOptions options;
  private Handler<GrpcServerRequest<Buffer, Buffer>> requestHandler;
  private final Map<String, List<MethodCallHandler<?, ?>>> methodCallHandlers = new HashMap<>();
  private final List<PathMatcher> pathMatchers = new ArrayList<>();
  private final Map<String, MethodTranscodingOptions> transcodingOptions = new HashMap<>();

  public GrpcServerImpl(Vertx vertx, GrpcServerOptions options) {
    this.options = new GrpcServerOptions(Objects.requireNonNull(options, "options is null"));
  }

  // Internal pojo, the name does not matter much at the moment
  private static class Details {
    final GrpcProtocol protocol;
    final WireFormat format;
    Details(GrpcProtocol protocol, WireFormat format) {
      this.protocol = protocol;
      this.format = format;
    }
  }

  private Details determine(String contentType) {
    WireFormat format;
    GrpcProtocol protocol;
    if (contentType != null) {
      Matcher matcher = CONTENT_TYPE_PATTERN.matcher(contentType);
      if (matcher.matches()) {
        if (matcher.group(1) != null) {
          protocol = matcher.group(2) == null ? GrpcProtocol.WEB : GrpcProtocol.WEB_TEXT;
        } else {
          protocol = GrpcProtocol.HTTP_2;
        }
        if (matcher.group(3) != null) {
          switch (matcher.group(4)) {
            case "proto":
              format = WireFormat.PROTOBUF;
              break;
            case "json":
              format = WireFormat.JSON;
              break;
            default:
              throw new UnsupportedOperationException("Not possible");
          }
        } else {
          format = WireFormat.PROTOBUF;
        }
        return new Details(protocol, format);
      } else {
        if (GrpcProtocol.TRANSCODING.mediaType().equals(contentType)) {
          protocol = GrpcProtocol.TRANSCODING;
          format = WireFormat.JSON;
          return new Details(protocol, format);
        } else {
          return null;
        }
      }
    } else {
      return null;
    }
  }

  @Override
  public void handle(HttpServerRequest httpRequest) {
    String contentType = httpRequest.getHeader(CONTENT_TYPE);
    Details details;
    if (contentType != null && (details = determine(contentType)) != null) {
      int errorCode = validate(httpRequest.version(), details.protocol, details.format);
      if (errorCode > 0) {
        httpRequest.response().setStatusCode(errorCode).end();
        return;
      }
    } else {
      httpRequest.response().setStatusCode(415).end();
      return;
    }

    PathMatcherLookupResult pathMatcherLookupResult = null;
    if (httpRequest.version() != HttpVersion.HTTP_2 && GrpcProtocol.TRANSCODING.mediaType().equals(httpRequest.headers().get(CONTENT_TYPE))) {
      for (PathMatcher pathMatcher : pathMatchers) {
        pathMatcherLookupResult = pathMatcher.lookup(httpRequest.method().name(), httpRequest.path(), httpRequest.query());
        if (pathMatcherLookupResult != null) {
          break;
        }
      }
    }

    GrpcMethodCall methodCall = lookupMethod(httpRequest, pathMatcherLookupResult);
    String fmn = methodCall.fullMethodName();
    List<MethodCallHandler<?, ?>> methods = methodCallHandlers.get(fmn);

    if (methods != null) {
      for (MethodCallHandler<?, ?> method : methods) {
        if (GrpcProtocol.TRANSCODING == details.protocol && details.protocol.mediaType().equals(httpRequest.headers().get(CONTENT_TYPE))) {
          handle(method, httpRequest, methodCall, pathMatcherLookupResult);
          return;
        }

        if (method.messageEncoder.format() == details.format && method.messageDecoder.format() == details.format) {
          handle(method, httpRequest, methodCall, details.protocol, details.format);
          return;
        }
      }
    }
    Handler<GrpcServerRequest<Buffer, Buffer>> handler = requestHandler;
    if (handler != null) {
      handle(httpRequest, methodCall, details.protocol, details.format, null, null, GrpcMessageDecoder.IDENTITY, GrpcMessageEncoder.IDENTITY, handler);
    } else {
      httpRequest.response().setStatusCode(500).end();
    }
  }

  private int validate(HttpVersion version, GrpcProtocol protocol, WireFormat format) {
    if (version != HttpVersion.HTTP_2) {
      if (!options.isGrpcWebEnabled() && !options.isGrpcTranscodingEnabled()) {
        log.trace("The server is not configured to handle HTTP/1.1 requests, sending error 505");
        return 505;
      }
    }
    // Check HTTP version compatibility
    if (!protocol.accepts(version)) {
      log.trace(protocol.name() + " not supported on " + version + ", sending error 415");
      return 415;
    }
    // Check config
    switch (protocol) {
      case WEB:
      case WEB_TEXT:
        if (!options.isGrpcWebEnabled()) {
          log.trace("gRPC-Web is not supported on HTTP/1.1, sending error 415");
          return 415;
        }
        break;
      case TRANSCODING:
        if (!options.isGrpcTranscodingEnabled()) {
          log.trace("gRPC transcoding is not supported on HTTP/1.1, sending error 415");
          return 415;
        }
        break;
    }
    return -1;
  }

  private GrpcMethodCall lookupMethod(HttpServerRequest request, PathMatcherLookupResult pathMatcherLookupResult) {
    if (request.version() == HttpVersion.HTTP_2) {
      return new GrpcMethodCall(request.path());
    }

    if (GrpcProtocol.TRANSCODING.mediaType().equals(request.headers().get(CONTENT_TYPE)) && pathMatcherLookupResult != null) {
      return new GrpcMethodCall("/" + pathMatcherLookupResult.getMethod());
    }

    return new GrpcMethodCall(request.path());
  }

  private <Req, Resp> void handle(MethodCallHandler<Req, Resp> method, HttpServerRequest request, GrpcMethodCall methodCall, PathMatcherLookupResult pathMatcherLookupResult) {
    String contentType = request.getHeader(CONTENT_TYPE);
    if (!contentType.equals(GrpcProtocol.TRANSCODING.mediaType())) {
      request.response().setStatusCode(415).end();
      return;
    }

    List<HttpVariableBinding> bindings = new ArrayList<>();
    if (request.version() != HttpVersion.HTTP_2 && GrpcProtocol.TRANSCODING.mediaType().equals(request.getHeader(CONTENT_TYPE))) {
      bindings.addAll(pathMatcherLookupResult.getVariableBindings());
    }

    MethodTranscodingOptions transcodingOptions = this.transcodingOptions.get(methodCall.fullMethodName());
    if (transcodingOptions == null) {
      request.response().setStatusCode(404).end();
      return;
    }

    handle(request, methodCall, GrpcProtocol.TRANSCODING, WireFormat.JSON, transcodingOptions, bindings, method.messageDecoder, method.messageEncoder, method);
  }

  private <Req, Resp> void handle(MethodCallHandler<Req, Resp> method, HttpServerRequest httpRequest, GrpcMethodCall methodCall, GrpcProtocol protocol, WireFormat format) {
    handle(httpRequest, methodCall, protocol, format, null, null, method.messageDecoder, method.messageEncoder, method);
  }

  private <Req, Resp> void handle(HttpServerRequest httpRequest,
                                  GrpcMethodCall methodCall,
                                  GrpcProtocol protocol,
                                  WireFormat format,
                                  MethodTranscodingOptions transcodingOptions,
                                  List<HttpVariableBinding> bindings,
                                  GrpcMessageDecoder<Req> messageDecoder,
                                  GrpcMessageEncoder<Resp> messageEncoder,
                                  Handler<GrpcServerRequest<Req, Resp>> handler) {
    io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
    GrpcServerRequestImpl<Req, Resp> grpcRequest;
    GrpcServerResponseImpl<Req, Resp> grpcResponse;
    switch (protocol) {
      case HTTP_2:
        grpcRequest = new Http2GrpcServerRequest<>(
          context,
          options.getScheduleDeadlineAutomatically(),
          protocol,
          format,
          options.getMaxMessageSize(),
          httpRequest,
          messageDecoder,
          methodCall);
        grpcResponse = new Http2GrpcServerResponse<>(
          context,
          grpcRequest,
          protocol,
          httpRequest.response(),
          messageEncoder);
        break;
      case WEB:
      case WEB_TEXT:
        grpcRequest = new WebGrpcServerRequest<>(
          context,
          options.getScheduleDeadlineAutomatically(),
          protocol,
          format,
          options.getMaxMessageSize(),
          httpRequest,
          messageDecoder,
          methodCall);
        grpcResponse = new WebGrpcServerResponse<>(
          context,
          grpcRequest,
          protocol,
          httpRequest.response(),
          messageEncoder);
        break;
      case TRANSCODING:
        grpcRequest = new TranscodingGrpcServerRequest<>(
          context,
          options.getScheduleDeadlineAutomatically(),
          protocol,
          format,
          options.getMaxMessageSize(),
          httpRequest,
          transcodingOptions.getBody(),
          bindings,
          messageDecoder,
          methodCall);
        grpcResponse = new TranscodingGrpcServerResponse<>(
          context,
          grpcRequest,
          protocol,
          httpRequest.response(),
          transcodingOptions.getResponseBody(),
          messageEncoder);
        break;
      default:
        throw new AssertionError();
    }
    if (options.getDeadlinePropagation() && grpcRequest.timeout() > 0L) {
      long deadline = System.currentTimeMillis() + grpcRequest.timeout;
      context.putLocal(GrpcLocal.CONTEXT_LOCAL_KEY, AccessMode.CONCURRENT, new GrpcLocal(deadline));
    }
    grpcResponse.init();
    grpcRequest.init(grpcResponse);
    grpcRequest.invalidMessageHandler(invalidMsg -> {
      if (invalidMsg instanceof MessageSizeOverflowException) {
        grpcRequest.response().status(GrpcStatus.RESOURCE_EXHAUSTED).end();
      } else {
        grpcResponse.cancel();
      }
    });
    context.dispatch(grpcRequest, handler);
  }

  public GrpcServer callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    this.requestHandler = handler;
    return this;
  }

  @Override
  public <Req, Resp> GrpcServer callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    if (handler != null) {
      MethodCallHandler<Req, Resp> p = new MethodCallHandler<>(serviceMethod.decoder(), serviceMethod.encoder(), handler);
      methodCallHandlers.compute(serviceMethod.fullMethodName(), (key, prev) -> {
        if (prev == null) {
          prev = new ArrayList<>();
        }
        for (int i = 0;i < prev.size();i++) {
          MethodCallHandler<?, ?> a = prev.get(i);
          if (a.messageDecoder.format() == serviceMethod.decoder().format() && a.messageEncoder.format() == serviceMethod.encoder().format()) {
            prev.set(i, p);
            return prev;
          }
        }
        prev.add(p);
        return prev;
      });
    } else {
      methodCallHandlers.compute(serviceMethod.fullMethodName(), (key, prev) -> {
        if (prev != null) {
          for (int i = 0;i < prev.size();i++) {
            MethodCallHandler<?, ?> a = prev.get(i);
            if (a.messageDecoder.format() == serviceMethod.decoder().format() && a.messageEncoder.format() == serviceMethod.encoder().format()) {
              prev.remove(i);
              break;
            }
          }
          if (prev.isEmpty()) {
            prev = null;
          }
        }
        return prev;
      });
    }
    return this;
  }

  @Override
  public <Req, Resp> GrpcServer callHandlerWithTranscoding(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler,
    MethodTranscodingOptions transcodingOptions) {
    this.callHandler(serviceMethod, handler);

    if (!options.isGrpcTranscodingEnabled()) {
      throw new IllegalStateException("gRPC transcoding is not enabled");
    }

    PathMatcherBuilder pmb = new PathMatcherBuilder();
    PathMatcherUtility.registerByHttpRule(pmb, transcodingOptions, serviceMethod.fullMethodName());

    this.pathMatchers.add(pmb.build());
    this.transcodingOptions.put(serviceMethod.fullMethodName(), transcodingOptions);

    return this;
  }

  private static class MethodCallHandler<Req, Resp> implements Handler<GrpcServerRequest<Req, Resp>> {

    final GrpcMessageDecoder<Req> messageDecoder;
    final GrpcMessageEncoder<Resp> messageEncoder;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodCallHandler(GrpcMessageDecoder<Req> messageDecoder, GrpcMessageEncoder<Resp> messageEncoder, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.messageDecoder = messageDecoder;
      this.messageEncoder = messageEncoder;
      this.handler = handler;
    }

    @Override
    public void handle(GrpcServerRequest<Req, Resp> grpcRequest) {
      handler.handle(grpcRequest);
    }
  }
}
