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
import io.vertx.grpc.server.Service;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcServer;

import com.google.protobuf.Descriptors;

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
public class StreamingService {

  public static final ServiceName SERVICE_NAME = ServiceName.create("streaming", "Streaming");


  /**
   * Source protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.Empty, examples.Item> Source = ServiceMethod.server(
    SERVICE_NAME,
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Empty.parser()));

  /**
   * Sink protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.Item, examples.Empty> Sink = ServiceMethod.server(
    SERVICE_NAME,
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));

  /**
   * Pipe protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.server(
    SERVICE_NAME,
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.Item.parser()));

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
   * Json server service methods.
   */
  public static final class Json {

    /**
     * Source json RPC server service method.
     */
    public static final ServiceMethod<examples.Empty, examples.Item> Source = ServiceMethod.server(
      SERVICE_NAME,
      "Source",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Empty.newBuilder()));

    /**
     * Sink json RPC server service method.
     */
    public static final ServiceMethod<examples.Item, examples.Empty> Sink = ServiceMethod.server(
      SERVICE_NAME,
      "Sink",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));

    /**
     * Pipe json RPC server service method.
     */
    public static final ServiceMethod<examples.Item, examples.Item> Pipe = ServiceMethod.server(
      SERVICE_NAME,
      "Pipe",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.Item.newBuilder()));

    /**
     * @return a mutable list of the known json RPC server service methods.
     */
    public static java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      all.add(Source);
      all.add(Sink);
      all.add(Pipe);
      return all;
    }
  }

  /**
   * Transcoded server service methods.
   */
  public static final class Transcoding {

    /**
     * @return a mutable list of the known transcoded RPC server service methods.
     */
    public static java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      return all;
    }
  }


  /**
   * Override this method to implement the Source RPC.
   */
  protected ReadStream<examples.Item> source(examples.Empty request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void source(examples.Empty request, WriteStream<examples.Item> response) {
    source(request)
      .handler(msg -> response.write(msg))
      .endHandler(msg -> response.end())
      .resume();
  }

  /**
   * Override this method to implement the Sink RPC.
   */
  protected Future<examples.Empty> sink(ReadStream<examples.Item> request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void sink(ReadStream<examples.Item> request, Promise<examples.Empty> response) {
    sink(request)
      .onSuccess(msg -> response.complete(msg))
      .onFailure(error -> response.fail(error));
  }

  /**
   * Override this method to implement the Pipe RPC.
   */
  protected ReadStream<examples.Item> pipe(ReadStream<examples.Item> request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void pipe(ReadStream<examples.Item> request, WriteStream<examples.Item> response) {
    pipe(request)
      .handler(msg -> response.write(msg))
      .endHandler(msg -> response.end())
      .resume();
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
      if (Source == serviceMethod || Json.Source == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.Empty, examples.Item>> handler = StreamingService.this::handle_source;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      if (Sink == serviceMethod || Json.Sink == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Empty>> handler = StreamingService.this::handle_sink;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      if (Pipe == serviceMethod || Json.Pipe == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Item>> handler = StreamingService.this::handle_pipe;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      return null;
    }

    private <Req, Resp> void bindHandler(Service service, ServiceMethod<Req, Resp> serviceMethod) {
      Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> handler = resolveHandler(serviceMethod);
      service.callHandler(serviceMethod, handler);
    }

    /**
     * Bind the contained service methods to the {@code server}.
     */
    public void to(GrpcServer server) {
      Service service = Service.service(SERVICE_NAME);
      service.descriptor(StreamingProto.getDescriptor().findServiceByName("Streaming"));
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        bindHandler(service, serviceMethod);
      }

      server.addService(service);
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

  private void handle_source(io.vertx.grpc.server.GrpcServerRequest<examples.Empty, examples.Item> request) {
    request.handler(msg -> {
      try {
        source(msg, request.response());
      } catch (RuntimeException err) {
        request.response().status(GrpcStatus.INTERNAL).end();
      }
    });
  }

  private void handle_sink(io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Empty> request) {
    Promise<examples.Empty> promise = Promise.promise();
    promise.future()
      .onFailure(err -> request.response().status(GrpcStatus.INTERNAL).end())
      .onSuccess(resp -> request.response().end(resp));
    try {
      sink(request, promise);
    } catch (RuntimeException err) {
      promise.tryFail(err);
    }
  }

  private void handle_pipe(io.vertx.grpc.server.GrpcServerRequest<examples.Item, examples.Item> request) {
    try {
      pipe(request, request.response());
    } catch (RuntimeException err) {
      request.response().status(GrpcStatus.INTERNAL).end();
    }
  }
}
