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
import io.vertx.grpc.server.StatusException;

import com.google.protobuf.Descriptors;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;

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
public class StreamingGrpcService extends StreamingService implements Service {

  /**
   * Streaming service name.
   */
  public static final ServiceName SERVICE_NAME = ServiceName.create("examples.grpc", "Streaming");

  /**
   * Streaming service descriptor.
   */
  public static final Descriptors.ServiceDescriptor SERVICE_DESCRIPTOR = Docs.getDescriptor().findServiceByName("Streaming");

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
  public static Service of(StreamingService service) {
    return builder(service).bind(all()).build();
  }

  /**
   * Source protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Empty, examples.grpc.Item> Source = ServiceMethod.server(
    SERVICE_NAME,
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Empty.newBuilder()));

  /**
   * Sink protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Item, examples.grpc.Empty> Sink = ServiceMethod.server(
    SERVICE_NAME,
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.newBuilder()));

  /**
   * Pipe protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Item, examples.grpc.Item> Pipe = ServiceMethod.server(
    SERVICE_NAME,
    "Pipe",
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


  /**
   * @return a free form builder that gives the opportunity to bind only certain methods of a service
   */
  public static Builder builder(StreamingService service) {
    return new Builder(service);
  }

  /**
   * Service builder.
   */
  public static class Builder implements ServiceBuilder {

    private final List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>();
    private final StreamingService instance;

    private Builder(StreamingService instance) {
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
        if (Source == serviceMethod) {
          Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Empty, examples.grpc.Item>> handler = this::handle_source;
          Handler<?> handler2 = handler;
          return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
        }
        if (Sink == serviceMethod) {
          Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Empty>> handler = this::handle_sink;
          Handler<?> handler2 = handler;
          return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
        }
        if (Pipe == serviceMethod) {
          Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Item>> handler = this::handle_pipe;
          Handler<?> handler2 = handler;
          return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
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
        request.response().status(StatusException.mapStatus(err)).end();
      }
    });
  }

  private void handle_pipe(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Item> request) {
    instance.pipe(request, request.response());
  }
    }
  }
}
