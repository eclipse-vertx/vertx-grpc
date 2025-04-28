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
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.ServiceBuilder;

import com.google.protobuf.Descriptors;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;

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

  @Override
  public void bind(GrpcServer server) {
    builder(this).bind(all()).build().bind(server);
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
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.HelloRequest.newBuilder()),
    SayHello_OPTIONS
  );

  /**
   * @return a free form builder that gives the opportunity to bind only certain methods of a service
   */
  public static Builder builder(GreeterService service) {
    return new Builder(service);
  }

  /**
   * Service builder.
   */
  public static class Builder implements ServiceBuilder {

    private final List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>();
    private final GreeterService instance;

    private Builder(GreeterService instance) {
      this.instance = instance;
    }

//    private void validate() {
//      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
//        if (resolveHandler(serviceMethod) == null) {
//          throw new IllegalArgumentException("Invalid service method:" + serviceMethod);
//        }
//      }
//    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    public <Req, Resp> ServiceBuilder bind(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
      throw new UnsupportedOperationException();
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
      return new Invoker();
    }

    private class Invoker implements Service {

      // Defensive copy
      private final List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>(Builder.this.serviceMethods);

      public ServiceName name() {
        return SERVICE_NAME;
      }

      public Descriptors.ServiceDescriptor descriptor() {
        return SERVICE_DESCRIPTOR;
      }

      /**
       * Bind the contained service methods to the {@code server}.
       */
      public void bind(GrpcServer server) {
        for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
          bindHandler(serviceMethod, server);
        }
      }

      private <Req, Resp> void bindHandler(ServiceMethod<Req, Resp> serviceMethod, GrpcServer server) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> handler = resolveHandler(serviceMethod);
        server.callHandler(serviceMethod, handler);
      }

      private <Req, Resp> Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
        if (SayHello == serviceMethod) {
          Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.HelloRequest, examples.grpc.HelloReply>> handler = this::handle_sayHello;
          Handler<?> handler2 = handler;
          return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
        }
        return null;
      }


  private void handle_sayHello(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.HelloRequest, examples.grpc.HelloReply> request) {
    request.handler(msg -> {
      try {
        instance.sayHello(msg, (res, err) -> {
          if (err == null) {
            request.response().end(res);
          } else {
            request.response().status(GrpcStatus.UNKNOWN).end();
          }
        });
      } catch (UnsupportedOperationException err) {
        request.response().status(GrpcStatus.UNIMPLEMENTED).end();
      } catch (RuntimeException err) {
        request.response().status(GrpcStatus.UNKNOWN).end();
      }
    });
  }
    }
  }
}
