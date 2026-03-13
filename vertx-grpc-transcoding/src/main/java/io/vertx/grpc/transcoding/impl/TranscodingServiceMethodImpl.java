package io.vertx.grpc.transcoding.impl;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.impl.GrpcInvocation;
import io.vertx.grpc.server.impl.GrpcServerRequestImpl;
import io.vertx.grpc.server.impl.GrpcServerResponseImpl;
import io.vertx.grpc.server.impl.MountPoint;
import io.vertx.grpc.server.impl.ProtocolHandler;
import io.vertx.grpc.transcoding.*;
import io.vertx.grpc.transcoding.impl.config.HttpTemplate;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TranscodingServiceMethodImpl<I, O> implements TranscodingServiceMethod<I, O>, MountPoint<I, O> {

  private final ServiceName serviceName;
  private final String methodName;
  private final GrpcMessageEncoder<O> encoder;
  private final GrpcMessageDecoder<I> decoder;
  private final MethodTranscodingOptions options;

  private final PathMatcher pathMatcher;

  public TranscodingServiceMethodImpl(ServiceName serviceName, String methodName, GrpcMessageEncoder<O> encoder, GrpcMessageDecoder<I> decoder) {
    this(serviceName, methodName, encoder, decoder, null);
  }

  public TranscodingServiceMethodImpl(ServiceName serviceName, String methodName, GrpcMessageEncoder<O> encoder, GrpcMessageDecoder<I> decoder, MethodTranscodingOptions options) {
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.encoder = encoder;
    this.decoder = decoder;
    this.options = options;

    // Init
    if (options != null) {
      PathMatcherBuilder pmb = new PathMatcherBuilder();
      PathMatcherUtility.registerByHttpRule(pmb, options, fullMethodName());
      this.pathMatcher = pmb.build();
    } else {
      this.pathMatcher = null;
    }
  }

  @Override
  public List<String> paths() {
    Set<String> paths = new HashSet<>();
    computePaths(options, paths);
    return new ArrayList<>(paths);
  }

  private void computePaths(MethodTranscodingOptions options, Set<String> paths) {
    if (options == null || options.getPath().equals(fullMethodName())) {
      paths.add(fullMethodName());
      return;
    }

    HttpTemplate tmpl = HttpTemplate.parse(options.getPath());
    StringBuilder sb = new StringBuilder();
    for (String a : tmpl.getSegments()) {
      if (a.equals("*") || (a.startsWith("{") && a.endsWith("}"))) {
        break;
      }
      sb.append('/').append(a);
    }
    paths.add(sb.toString());
    List<MethodTranscodingOptions> extra = options.getAdditionalBindings();
    if (extra != null) {
      for (MethodTranscodingOptions o : extra) {
        computePaths(o, paths);
      }
    }
  }

  public GrpcInvocation<I, O> accept(HttpServerRequest httpRequest) {
    if (!httpRequest.getHeader(HttpHeaders.CONTENT_TYPE).equals(GrpcProtocol.TRANSCODING.mediaType())) {
      return null;
    }

    PathMatcherLookupResult res = pathMatcher == null ? null : pathMatcher.lookup(httpRequest.method().name(), httpRequest.path(), httpRequest.query());
    if (res != null) {
      List<HttpVariableBinding> bindings = new ArrayList<>(res.getVariableBindings());
      io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
      TranscodingMessageDecoder<I> messageDecoder = new TranscodingMessageDecoder<>(decoder, options.getBody(), bindings);
      TranscodingMessageDeframer deframer = new TranscodingMessageDeframer();
      GrpcServerRequestImpl<I, O> grpcRequest = new GrpcServerRequestImpl<>(
        context,
        GrpcProtocol.TRANSCODING,
        WireFormat.JSON,
        httpRequest,
        deframer,
        messageDecoder,
        new GrpcMethodCall("/" + res.getMethod()));
      ProtocolHandler protocolHandler = new TranscodingProtocolHandler(context, httpRequest, options.getResponseBody());
      GrpcServerResponseImpl<I, O> grpcResponse = new GrpcServerResponseImpl<>(
        context,
        grpcRequest,
        protocolHandler,
        GrpcProtocol.TRANSCODING,
        httpRequest.response(),
        encoder);
      return new GrpcInvocation<>(grpcRequest, grpcResponse);
    } else if (options == null) {
      io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
      TranscodingMessageDeframer deframer = new TranscodingMessageDeframer();
      TranscodingMessageDecoder<I> messageDecoder = new TranscodingMessageDecoder<>(decoder, null, List.of());
      GrpcServerRequestImpl<I, O> grpcRequest = new GrpcServerRequestImpl<>(
        context,
        GrpcProtocol.TRANSCODING,
        WireFormat.JSON,
        httpRequest,
        deframer,
        messageDecoder,
        new GrpcMethodCall("/" + methodName));
      ProtocolHandler protocolHandler = new TranscodingProtocolHandler(context, httpRequest, null);
      GrpcServerResponseImpl<I, O> grpcResponse = new GrpcServerResponseImpl<>(
        context,
        grpcRequest,
        protocolHandler,
        GrpcProtocol.TRANSCODING,
        httpRequest.response(),
        encoder);
      return new GrpcInvocation<>(grpcRequest, grpcResponse);
    }

    return null;
  }

  @Override
  public ServiceName serviceName() {
    return serviceName;
  }

  @Override
  public String methodName() {
    return methodName;
  }

  @Override
  public GrpcMessageDecoder<I> decoder() {
    return decoder;
  }

  @Override
  public GrpcMessageEncoder<O> encoder() {
    return encoder;
  }

  @Override
  public MethodTranscodingOptions options() {
    return options;
  }
}
