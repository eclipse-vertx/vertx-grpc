package io.vertx.grpc.transcoding.impl;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.impl.GrpcInvocation;
import io.vertx.grpc.server.impl.GrpcServerRequestImpl;
import io.vertx.grpc.server.impl.GrpcServerResponseImpl;
import io.vertx.grpc.server.impl.MountPoint;
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

  public TranscodingServiceMethodImpl(ServiceName serviceName, String methodName, GrpcMessageEncoder<O> encoder, GrpcMessageDecoder<I> decoder, MethodTranscodingOptions options) {

    this.serviceName = serviceName;
    this.methodName = methodName;
    this.encoder = encoder;
    this.decoder = decoder;
    this.options = options;

    // Init
    PathMatcherBuilder pmb = new PathMatcherBuilder();
    PathMatcherUtility.registerByHttpRule(pmb, options, fullMethodName());

    this.pathMatcher = pmb.build();
  }

  @Override
  public List<String> paths() {
    Set<String> paths = new HashSet<>();
    computePaths(options, paths);
    return new ArrayList<>(paths);
  }

  private void computePaths(MethodTranscodingOptions options, Set<String> paths) {
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
    PathMatcherLookupResult res = pathMatcher.lookup(httpRequest.method().name(), httpRequest.path(), httpRequest.query());
    if (res != null) {
      List<HttpVariableBinding> bindings = new ArrayList<>(res.getVariableBindings());
      io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
      GrpcServerRequestImpl<I, O> grpcRequest = new TranscodingGrpcServerRequest<>(
        context,
        httpRequest,
        options.getBody(),
        bindings,
        decoder,
        new GrpcMethodCall("/" + res.getMethod()));
      GrpcServerResponseImpl<I, O> grpcResponse = new TranscodingGrpcServerResponse<>(
        context,
        grpcRequest,
        GrpcProtocol.TRANSCODING,
        httpRequest.response(),
        options.getResponseBody(),
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
