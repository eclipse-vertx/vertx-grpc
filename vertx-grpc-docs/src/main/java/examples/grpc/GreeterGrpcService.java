package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.MethodCardinality;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.ServiceContainer;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.StatusException;

import com.google.protobuf.Descriptors;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * <p>Provides support for RPC methods implementations of the Greeter gRPC service.</p>
 *
 * <p>The following methods of this class should be overridden to provide an implementation of the service:</p>
 * <ul>
 *   <li>SayHello</li>
 * </ul>
 */
public class GreeterGrpcService extends GreeterService implements Service {

  /**
   * Greeter service name.
   */
  public static final ServiceName SERVICE_NAME = ServiceName.create("examples.grpc", "Greeter");

  /**
   * Greeter service descriptor.
   */
  public static final Descriptors.ServiceDescriptor SERVICE_DESCRIPTOR = Docs.getDescriptor().findServiceByName("Greeter");

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
  public static Service of(GreeterService service) {
    return builder(service).bind(all()).build();
  }

  /**
   * @return a mutable list of the known protobuf RPC server service methods.
   */
  public static java.util.List<ServiceMethod<?, ?>> all() {
    java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
    all.add(SayHello);
    return all;
  }


  private static final io.vertx.grpc.transcoding.MethodTranscodingOptions SayHello_OPTIONS = new io.vertx.grpc.transcoding.MethodTranscodingOptions()
    .setSelector("")
    .setHttpMethod(HttpMethod.valueOf("GET"))
    .setPath("/v1/hello/{name}")
    .setBody("")
    .setResponseBody("")
  ;

  /**
   * SayHello transcoded RPC server service method.
   */
  public static final io.vertx.grpc.transcoding.TranscodingServiceMethod<examples.grpc.HelloRequest, examples.grpc.HelloReply> SayHello = io.vertx.grpc.transcoding.TranscodingServiceMethod.server(
    SERVICE_NAME,
    "SayHello",
    MethodCardinality.UNARY,
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.HelloRequest.newBuilder()),
    SayHello_OPTIONS
  );

  private final RequestHandler handler = new RequestHandler(this, all());

  @Override
  public <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
    return handler.handler(method);
  }

  @Override
  public List<ServiceMethod<?, ?>> methods() {
    return handler.methods();
  }

  /**
   * @return a free form builder that gives the opportunity to bind only certain methods of a service
   */
  public static Builder builder(GreeterService service) {
    return new Builder(service);
  }

  /**
   * Service builder.
   */
  public static class Builder {

    private final List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>();
    private final GreeterService instance;

    private Builder(GreeterService instance) {
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

    public Service build() {
      return new RequestHandler(instance, new ArrayList<>(serviceMethods));
    }
  }

  private static class RequestHandler implements Service {

    private final GreeterService instance;
    private final List<ServiceMethod<?, ?>> serviceMethods;
    private final Map<String, Handler<GrpcServerRequest<?, ?>>> handlers;

    public RequestHandler(GreeterService instance, List<ServiceMethod<?, ?>> serviceMethods) {
      Map<String, Handler<GrpcServerRequest<?, ?>>> handlers = new HashMap<>();
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        Handler<GrpcServerRequest<?, ?>> handler = resolveHandler(serviceMethod);
        handlers.put(serviceMethod.methodName(), handler);
      }

      this.instance = instance;
      this.handlers = handlers;
      this.serviceMethods = serviceMethods;
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

    private <Req, Resp> Handler<GrpcServerRequest<?, ?>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
      if (SayHello == serviceMethod) {
        Handler<GrpcServerRequest<examples.grpc.HelloRequest, examples.grpc.HelloReply>> handler = this::handle_sayHello;
        return (Handler) handler;
      }
      return null;
    }


  private void handle_sayHello(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.HelloRequest, examples.grpc.HelloReply> request) {
    request.handler(msg -> {
      instance.sayHello(msg, (res, err) -> {
        if (err == null) {
          request.response().end(res);
        } else {
          request.response().fail(err);
        }
      });
    });
  }
  }
}
