package io.vertx.grpc.transcoding.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.impl.GrpcInvocation;
import io.vertx.grpc.server.impl.MountPoint;
import io.vertx.grpc.server.impl.HttpGrpcOutboundStream;
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
  private final MethodCardinality cardinality;
  private final GrpcMessageEncoder<O> encoder;
  private final GrpcMessageDecoder<I> decoder;
  private final MethodTranscodingOptions options;

  private final PathMatcher pathMatcher;

  public TranscodingServiceMethodImpl(ServiceName serviceName, String methodName, GrpcMessageEncoder<O> encoder, GrpcMessageDecoder<I> decoder) {
    this(serviceName, methodName, null, encoder, decoder, null);
  }

  public TranscodingServiceMethodImpl(ServiceName serviceName, String methodName, GrpcMessageEncoder<O> encoder, GrpcMessageDecoder<I> decoder, MethodTranscodingOptions options) {
    this(serviceName, methodName, null, encoder, decoder, options);
  }

  public TranscodingServiceMethodImpl(ServiceName serviceName, String methodName, MethodCardinality cardinality, GrpcMessageEncoder<O> encoder, GrpcMessageDecoder<I> decoder, MethodTranscodingOptions options) {
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.cardinality = cardinality;
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
    String verb = tmpl.getVerb();
    if (verb != null && !verb.isEmpty()) {
      sb.append(':').append(verb);
    }
    paths.add(sb.toString());
    List<MethodTranscodingOptions> extra = options.getAdditionalBindings();
    if (extra != null) {
      for (MethodTranscodingOptions o : extra) {
        computePaths(o, paths);
      }
    }
  }

  public GrpcInvocation accept(HttpServerRequest httpRequest, WireFormat format) {
    if (!httpRequest.getHeader(HttpHeaders.CONTENT_TYPE).equals(GrpcProtocol.TRANSCODING.mediaType())) {
      return null;
    }

    PathMatcherLookupResult res = pathMatcher == null ? null : pathMatcher.lookup(httpRequest.method().name(), httpRequest.path(), httpRequest.query());
    if (res != null) {
      List<HttpVariableBinding> bindings = new ArrayList<>(res.getVariableBindings());
      io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
      TranscodingMessageDeframer deframer = new TranscodingMessageDeframer(format) {
        @Override
        protected Buffer decode(Buffer buffer) throws InvalidMessageException {
          Buffer transcoded;
          try {
            transcoded = MessageWeaver.weaveRequestMessage(buffer, bindings, res.getBodyFieldPath(), decoder.messageDescriptor());
          } catch (DecodeException e) {
            throw new TranscodingInvalidMessageException(e);
          }
          return transcoded;
        }
      };
      HttpGrpcOutboundStream protocolHandler = new TranscodingGrpcOutboundStream(context, httpRequest, options.getResponseBody(), deframer);
      return new GrpcInvocation(deframer, protocolHandler);
    } else if (options == null) {
      io.vertx.core.internal.ContextInternal context = ((HttpServerRequestInternal) httpRequest).context();
      TranscodingMessageDeframer deframer = new TranscodingMessageDeframer(format);
      HttpGrpcOutboundStream protocolHandler = new TranscodingGrpcOutboundStream(context, httpRequest, null, deframer);
      return new GrpcInvocation(deframer, protocolHandler);
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
  public MethodCardinality cardinality() {
    return cardinality;
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
