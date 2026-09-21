package examples.grpc;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.MethodCardinality;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcMessageValidator;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;

import com.google.protobuf.Descriptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * <p>Provides support for RPC methods implementations of the Streaming gRPC service.</p>
 *
 * <p>The following methods of this class should be overridden to provide an implementation of the service:</p>
 * <ul>
 *   <li>Source</li>
 *   <li>Sink</li>
 *   <li>Pipe</li>
 * </ul>
 */
public class StreamingGrpcService extends examples.grpc.StreamingService implements Service {

  /**
   * Streaming service name.
   */
  public static final ServiceName SERVICE_NAME = ServiceName.create("examples.grpc", "Streaming");

  /**
   * Streaming service descriptor.
   */
  public static final Descriptors.ServiceDescriptor SERVICE_DESCRIPTOR = examples.grpc.Docs.getDescriptor().findServiceByName("Streaming");

  @Override
  public ServiceName name() {
    return SERVICE_NAME;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return SERVICE_DESCRIPTOR;
  }

  /**
   * @return a service binding all methods of the given {@code service}
   */
  public static Service of(examples.grpc.StreamingService service) {
    return builder(service).bind(all()).build();
  }

  /**
   * Source protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Empty, examples.grpc.Item> Source = ServiceMethod.server(
    SERVICE_NAME,
    "Source",
    MethodCardinality.SERVER_STREAMING,
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Empty.newBuilder()));

  /**
   * Sink protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Item, examples.grpc.Empty> Sink = ServiceMethod.server(
    SERVICE_NAME,
    "Sink",
    MethodCardinality.CLIENT_STREAMING,
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.newBuilder()));

  /**
   * Pipe protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Item, examples.grpc.Item> Pipe = ServiceMethod.server(
    SERVICE_NAME,
    "Pipe",
    MethodCardinality.BIDI_STREAMING,
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.newBuilder()));

  /**
   * @return a mutable list of the known protobuf RPC server service methods.
   */
  public static java.util.List<ServiceMethod<?, ?>> all() {
    java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
    all.add(Source);
    all.add(Sink);
    all.add(Pipe);
    return all;
  }


  private final RequestHandler handler = new RequestHandler(this, all());

  @Override
  public <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
    return handler.handler(method);
  }

  private GrpcMessageValidator<?> validator;

  /**
   * Set the validator applied to the request messages of every method of this service.
   *
   * @param validator the request message validator
   * @return a reference to this, so the API can be used fluently
   */
  public StreamingGrpcService validator(GrpcMessageValidator<?> validator) {
    this.validator = validator;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Req, Resp> GrpcMessageValidator<? super Req> validator(ServiceMethod<Req, Resp> method) {
    return (GrpcMessageValidator<? super Req>) validator;
  }

  @Override
  public List<ServiceMethod<?, ?>> methods() {
    return handler.methods();
  }

  /**
   * @return a free form builder that gives the opportunity to bind only certain methods of a service
   */
  public static Builder builder(examples.grpc.StreamingService service) {
    return new Builder(service);
  }

  /**
   * Service builder.
   */
  public static class Builder {

    private final List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>();
    private final examples.grpc.StreamingService instance;
    private GrpcMessageValidator<?> validator;

    private Builder(examples.grpc.StreamingService instance) {
      this.instance = instance;
    }

    /**
     * @return this builder
     */
    public Builder bind(List<ServiceMethod<?, ?>> methods) {
      serviceMethods.addAll(methods);
      return this;
    }

    /**
     * @return this builder
     */
    public Builder bind(ServiceMethod<?, ?>... methods) {
      return bind(java.util.Arrays.asList(methods));
    }

    /**
     * Set the validator applied to the request messages of the bound methods.
     *
     * @param validator the request message validator
     * @return this builder
     */
    public Builder validator(GrpcMessageValidator<?> validator) {
      this.validator = validator;
      return this;
    }

    public Service build() {
      return new RequestHandler(instance, new ArrayList<>(serviceMethods), validator);
    }
  }

  private static class RequestHandler implements Service {

    private final examples.grpc.StreamingService instance;
    private final List<ServiceMethod<?, ?>> serviceMethods;
    private final Map<String, Handler<GrpcServerRequest<?, ?>>> handlers;
    private final GrpcMessageValidator<?> validator;

    public RequestHandler(examples.grpc.StreamingService instance, List<ServiceMethod<?, ?>> serviceMethods) {
      this(instance, serviceMethods, null);
    }

    public RequestHandler(examples.grpc.StreamingService instance, List<ServiceMethod<?, ?>> serviceMethods, GrpcMessageValidator<?> validator) {
      Map<String, Handler<GrpcServerRequest<?, ?>>> handlers = new HashMap<>();
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        Handler<GrpcServerRequest<?, ?>> handler = resolveHandler(serviceMethod);
        handlers.put(serviceMethod.methodName(), handler);
      }

      this.instance = instance;
      this.handlers = handlers;
      this.serviceMethods = serviceMethods;
      this.validator = validator;
    }

    @Override
    public ServiceName name() {
      return SERVICE_NAME;
    }

    @Override
    public Descriptors.ServiceDescriptor descriptor() {
      return SERVICE_DESCRIPTOR;
    }

    @Override
    public List<ServiceMethod<?, ?>> methods() {
      return serviceMethods;
    }

    @Override
    public <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
      Handler<GrpcServerRequest<?, ?>> handler = handlers.get(method.methodName());
      if (handler != null) {
        return (Handler)handler;
      } else {
        return Service.super.handler(method);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Req, Resp> GrpcMessageValidator<? super Req> validator(ServiceMethod<Req, Resp> method) {
      return (GrpcMessageValidator<? super Req>) validator;
    }

    private <Req, Resp> Handler<GrpcServerRequest<?, ?>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
      if (Source == serviceMethod) {
        Handler<GrpcServerRequest<examples.grpc.Empty, examples.grpc.Item>> handler = this::handle_source;
        return (Handler) handler;
      }
      if (Sink == serviceMethod) {
        Handler<GrpcServerRequest<examples.grpc.Item, examples.grpc.Empty>> handler = this::handle_sink;
        return (Handler) handler;
      }
      if (Pipe == serviceMethod) {
        Handler<GrpcServerRequest<examples.grpc.Item, examples.grpc.Item>> handler = this::handle_pipe;
        return (Handler) handler;
      }
      return null;
    }


  private void handle_source(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Empty, examples.grpc.Item> request) {
    request.handler(msg -> {
      instance.source(msg, request.response());
    });
  }

  private void handle_sink(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Empty> request) {
    instance.sink(request, (res, err) -> {
      if (err == null) {
        request.response().end(res);
      } else {
        request.response().fail(err);
      }
    });
  }

  private void handle_pipe(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Item> request) {
    instance.pipe(request, request.response());
  }
  }
}
