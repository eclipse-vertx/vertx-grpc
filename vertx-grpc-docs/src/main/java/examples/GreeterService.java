package examples;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceMetadata;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcServer;

import com.google.protobuf.Descriptors;

import java.util.List;

/**
 * <p>Provides support for RPC methods implementations of the Greeter gRPC service.</p>
 *
 * <p>The following methods of this class should be overridden to provide an implementation of the service:</p>
 * <ul>
 *   <li>SayHello</li>
 * </ul>
 */
public class GreeterService {

  /**
   * SayHello protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = ServiceMethod.server(
    ServiceName.create("helloworld", "Greeter"),
    "SayHello",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.HelloRequest.parser()));

  /**
   * @return a mutable list of the known protobuf RPC server service methods.
   */
  public static java.util.List<ServiceMethod<?, ?>> all() {
    java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
    all.add(SayHello);
    return all;
  }

  /**
  * Service metadata.
  */
  public static final class Metadata implements ServiceMetadata {
    @Override
    public ServiceName getServiceName() {
      return ServiceName.create("helloworld", "Greeter");
    }

    @Override
    public Descriptors.ServiceDescriptor getServiceDescriptor() {
      return HelloWorldProto.getDescriptor().findServiceByName("Greeter");
    }
  }

  /**
   * Json server service methods.
   */
  public static final class Json {

    /**
     * SayHello json RPC server service method.
     */
    public static final ServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = ServiceMethod.server(
      ServiceName.create("helloworld", "Greeter"),
      "SayHello",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.HelloRequest.newBuilder()));

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
    public static final io.vertx.grpc.transcoding.TranscodingServiceMethod<examples.HelloRequest, examples.HelloReply> SayHello = io.vertx.grpc.transcoding.TranscodingServiceMethod.server(
      ServiceName.create("helloworld", "Greeter"),
      "SayHello",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.HelloRequest.newBuilder()),
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
  protected Future<examples.HelloReply> sayHello(examples.HelloRequest request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void sayHello(examples.HelloRequest request, Promise<examples.HelloReply> response) {
    sayHello(request)
      .onSuccess(msg -> response.complete(msg))
      .onFailure(error -> response.fail(error));
  }

  /**
   * Service method to RPC method binder.
   */
  public class Binder {

    private final List<ServiceMethod<?, ?>> serviceMethods;

    private Binder(List<ServiceMethod<?, ?>> serviceMethods) {
      this.serviceMethods = serviceMethods;
    }

    private void validate() {
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        if (resolveHandler(serviceMethod) == null) {
          throw new IllegalArgumentException("Invalid service method:" + serviceMethod);
        }
      }
    }

    private <Req, Resp> Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
      if (SayHello == serviceMethod || Json.SayHello == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.HelloRequest, examples.HelloReply>> handler = GreeterService.this::handle_sayHello;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      return null;
    }

    private <Req, Resp> void bindHandler(GrpcServer server, ServiceMethod<Req, Resp> serviceMethod) {
      Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> handler = resolveHandler(serviceMethod);
      server.callHandler(serviceMethod, handler);
    }

    /**
     * Bind the contained service methods to the {@code server}.
     */
    public void to(GrpcServer server) {
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        bindHandler(server, serviceMethod);
      }
    }
  }

  /**
   * @return a binder for the list of {@code methods}
   */
  public Binder bind(List<ServiceMethod<?, ?>> methods) {
    Binder binder = new Binder(methods);
    binder.validate();
    return binder;
  }

  /**
   * @return a binder for the {@code methods}
   */
  public Binder bind(ServiceMethod<?, ?>... methods) {
    return bind(java.util.Arrays.asList(methods));
  }

  private void handle_sayHello(io.vertx.grpc.server.GrpcServerRequest<examples.HelloRequest, examples.HelloReply> request) {
    Promise<examples.HelloReply> promise = Promise.promise();
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
