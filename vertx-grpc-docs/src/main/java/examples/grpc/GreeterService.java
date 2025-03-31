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
public class GreeterService implements Greeter, Service {

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
    builder().bind(all()).build().bind(server);
  }

  /**
   * SayHello protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.HelloRequest, examples.grpc.HelloReply> SayHello = ServiceMethod.server(
    SERVICE_NAME,
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.HelloRequest.parser()));

  /**
   * @return a mutable list of the known protobuf RPC server service methods.
   */
  public static java.util.List<ServiceMethod<?, ?>> all() {
    java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
    all.add(SayHello);
    return all;
  }

  /**
   * Json server service methods.
   */
  public static final class Json {

    /**
     * SayHello json RPC server service method.
     */
    public static final ServiceMethod<examples.grpc.HelloRequest, examples.grpc.HelloReply> SayHello = ServiceMethod.server(
      SERVICE_NAME,
      "SayHello",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.HelloRequest.newBuilder()));

    /**
     * @return a mutable list of the known json RPC server service methods.
     */
    public static java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      all.add(SayHello);
      return all;
    }
  }

  /**
   * Transcoded server service methods.
   */
  public static final class Transcoding {

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
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.HelloRequest.newBuilder()),
      SayHello_OPTIONS);

    /**
     * @return a mutable list of the known transcoded RPC server service methods.
     */
    public static java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      all.add(SayHello);
      return all;
    }
  }


  /**
   * Override this method to implement the SayHello RPC.
   */
  public Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void sayHello(examples.grpc.HelloRequest request, Promise<examples.grpc.HelloReply> response) {
    sayHello(request)
      .onSuccess(msg -> response.complete(msg))
      .onFailure(error -> response.fail(error));
  }

  private <Req, Resp> Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
    if (SayHello == serviceMethod || Json.SayHello == serviceMethod) {
      Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.HelloRequest, examples.grpc.HelloReply>> handler = GreeterService.this::handle_sayHello;
      Handler<?> handler2 = handler;
      return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
    }
    return null;
  }

  /**
   * @return a free form builder that gives the opportunity to bind only certain methods of a service
   */
  public Builder builder() {
    return new Builder();
  }

  /**
   * Service builder.
   */
  public class Builder implements ServiceBuilder {

    private final List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>();

    private void validate() {
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        if (resolveHandler(serviceMethod) == null) {
          throw new IllegalArgumentException("Invalid service method:" + serviceMethod);
        }
      }
    }

    private <Req, Resp> Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
      if (SayHello == serviceMethod || Json.SayHello == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.HelloRequest, examples.grpc.HelloReply>> handler = GreeterService.this::handle_sayHello;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      return null;
    }

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
      // Defensive copy
      List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>(Builder.this.serviceMethods);
      return new Service() {
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
      };
    }
  }

  private void handle_sayHello(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.HelloRequest, examples.grpc.HelloReply> request) {
    Promise<examples.grpc.HelloReply> promise = Promise.promise();
    request.handler(msg -> {
      try {
        sayHello(msg, promise);
      } catch (RuntimeException err) {
        promise.tryFail(err);
      }
    });
    promise.future()
      .onFailure(err -> request.response().status(GrpcStatus.INTERNAL).end())
      .onSuccess(resp -> request.response().end(resp));
  }
}
