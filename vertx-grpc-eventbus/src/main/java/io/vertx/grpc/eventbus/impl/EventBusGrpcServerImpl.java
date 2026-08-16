package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.ServiceMethodInvoker;
import io.vertx.grpc.server.impl.GrpcDispatcher;

import java.util.*;
import java.util.stream.Collectors;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;

public class EventBusGrpcServerImpl extends EventBusGrpcEndpoint implements EventBusGrpcServer {

  private final Vertx vertx;
  private final Map<String, ServiceConsumer> consumers = new HashMap<>();
  private final Set<WireFormat> supportedWireFormats;
  private final long maxPingTimeout;

  private EventBusGrpcServerImpl(Vertx vertx, EventBusGrpcServerOptions options) {
    super(vertx, vertx.eventBus(), "grpc.eb.server.", WireFormat.PROTOBUF, 0L, 0L);
    this.vertx = vertx;
    this.supportedWireFormats = new LinkedHashSet<>(options.getSupportedWireFormats());
    this.maxPingTimeout = options.getMaxPingTimeout().toMillis();
  }

  /**
   * How long a client that advertised {@code header} as its ping timeout may go unheard, the very deadline the client applies to this server, so a hiccup that one side rides out
   * does not cost the stream on the other. A client that advertises nothing, or more than this server honours, is held to {@code maxPingTimeout} instead: every remote endpoint gets a
   * deadline, so a client that goes away without a trace cannot leave its streams registered here.
   */
  private long remoteTimeout(String header) {
    if (header != null) {
      try {
        long advertised = Long.parseLong(header);
        if (advertised > 0) {
          return Math.min(advertised, maxPingTimeout);
        }
      } catch (NumberFormatException ignored) {
      }
    }
    return maxPingTimeout;
  }

  public static Future<EventBusGrpcServer> create(Vertx vertx, EventBusGrpcServerOptions options) {
    EventBusGrpcServerImpl server = new EventBusGrpcServerImpl(vertx, options);
    return server.bind().map(server);
  }

  @Override
  public <Req, Resp> EventBusGrpcServerImpl callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    String serviceFqn = serviceMethod.serviceName().fullyQualifiedName();

    ServiceConsumer consumer = consumers.get(serviceFqn);

    Service service;
    if (consumer == null) {
      if (handler == null) {
        return this;
      }
      service = new SimpleService(serviceMethod.serviceName());
      addService(service);
    } else {
      service = consumer.service;
    }

    if (service instanceof SimpleService) {
      SimpleService simpleService = (SimpleService) service;
      String methodName = serviceMethod.methodName();
      if (handler != null) {
        simpleService.handlers.put(methodName, new MethodHandler<>(serviceMethod, handler));
        simpleService.methods.add(serviceMethod);
      } else {
        throw new UnsupportedOperationException("Not yet implemented");
      }
    } else {
      throw new IllegalStateException();
    }

    return this;
  }

  @Override
  public EventBusGrpcServer addService(Service service) {
    for (ServiceConsumer consumer : consumers.values()) {
      if (consumer.service.name().equals(service.name())) {
        throw new IllegalStateException("Duplicated name: " + service.name().name());
      }
    }

    String serviceFqn = service.name().fullyQualifiedName();
    Adapter adapter = new Adapter(serviceFqn, service);
    MessageConsumer<Object> consumer = eventBus().consumer(serviceFqn, adapter);
    consumers.put(serviceFqn, new ServiceConsumer(consumer, service));

    return this;
  }

  @Override
  public List<Service> services() {
    return consumers
      .values()
      .stream()
      .map(sc -> sc.service)
      .collect(Collectors.toList());
  }

  @Override
  public Future<Void> close() {
    List<Future<Void>> futures = new ArrayList<>();
    for (ServiceConsumer consumer : consumers.values()) {
      futures.add(consumer.consumer.unregister());
      futures.add(consumer.service.close());
    }
    consumers.clear();
    futures.add(closeStreams());
    return Future.all(futures).mapEmpty();
  }

  private static class MethodHandler<Req, Resp> implements ServiceMethodInvoker<Req, Resp> {
    final ServiceMethod<Req, Resp> serviceMethod;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.serviceMethod = serviceMethod;
      this.handler = handler;
    }

    @Override
    public void invoke(GrpcServerRequest<Req, Resp> request) {
      handler.handle(request);
    }
  }

  private static class ServiceConsumer {

    private final MessageConsumer<Object> consumer;
    private final Service service;

    public ServiceConsumer(MessageConsumer<Object> consumer, Service service) {
      this.consumer = consumer;
      this.service = service;
    }
  }

  private static class SimpleService implements Service {

    private final List<ServiceMethod<?, ?>> methods;
    private final ServiceName serviceName;
    private final Map<String, MethodHandler<?, ?>> handlers;

    public SimpleService(ServiceName serviceName) {
      this.serviceName = serviceName;
      this.methods = new ArrayList<>();
      this.handlers = new HashMap<>();
    }

    @Override
    public ServiceName name() {
      return serviceName;
    }

    @Override
    public Descriptors.ServiceDescriptor descriptor() {
      return null;
    }

    @Override
    public List<ServiceMethod<?, ?>> methods() {
      return methods;
    }

    @Override
    public <Req, Resp> ServiceMethodInvoker<Req, Resp> invoker(ServiceMethod<Req, Resp> method) {
      MethodHandler<?, ?> methodHandler = handlers.get(method.methodName());
      if (methodHandler != null) {
        return (ServiceMethodInvoker<Req, Resp>) methodHandler;
      } else {
        return Service.super.invoker(method);
      }
    }
  }

  private class Adapter implements Handler<Message<Object>> {

    private final String serviceFqn;
    private final Service service;

    public Adapter(String serviceFqn, Service service) {
      this.serviceFqn = serviceFqn;
      this.service = service;
    }

    @Override
    public void handle(Message<Object> message) {
      String methodName = message.headers().get(EventBusHeaders.ACTION);
      if (methodName == null) {
        message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.ACTION + "' header");
        return;
      }
      String wireFormatName = message.headers().get(EventBusHeaders.WIRE_FORMAT);
      if (wireFormatName == null) {
        message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.WIRE_FORMAT + "' header");
        return;
      }

      WireFormat wireFormat;
      if (ProtobufWireFormat.NAME.equals(wireFormatName)) {
        wireFormat = WireFormat.PROTOBUF;
      } else if (JsonWireFormat.NAME.equals(wireFormatName)) {
        wireFormat = WireFormat.JSON;
      } else {
        message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Unknown wire format: " + wireFormatName);
        return;
      }

      if (!supportedWireFormats.contains(wireFormat)) {
        message.fail(GrpcStatus.UNIMPLEMENTED.code, "Unsupported wire format: " + wireFormat);
        return;
      }

      ServiceMethod<?, ?> serviceMethod = null;
      for (ServiceMethod<?, ?> candidate : service.methods()) {
        if (candidate.methodName().equals(methodName)) {
          serviceMethod = candidate;
          break;
        }
      }

      if (serviceMethod == null) {
        message.fail(GrpcStatus.UNIMPLEMENTED.code, "Method not found: " + methodName);
        return;
      }
      if (serviceMethod.clientStreaming() || serviceMethod.serverStreaming()) {
        dispatchStreaming(message, serviceMethod, wireFormat);
      } else {
        dispatchUnary(message, serviceMethod, wireFormat);
      }
    }

    private <Req, Resp> void dispatchUnary(Message<Object> message, ServiceMethod<Req, Resp> serviceMethod, WireFormat wireFormat) {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();

      ServiceMethodInvoker<Req, Resp> invoker;
      try {
        invoker = service.invoker(serviceMethod);
      } catch (Exception e) {
        message.fail(GrpcStatus.INTERNAL.code, GrpcStatus.INTERNAL.name());
        return;
      }

      MultiMap headers = MultiMap.caseInsensitiveMultiMap();
      EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);

      EventBusGrpcServerUnaryCall stream = new EventBusGrpcServerUnaryCall(context, message, wireFormat);

      GrpcMethodCall methodCall = new GrpcMethodCall(serviceMethod.serviceName().pathOf(serviceMethod.methodName()));

      GrpcDispatcher<Req, Resp> dispatcher = new GrpcDispatcher<>(
        stream,
        context,
        null,
        wireFormat,
        serviceMethod.decoder(),
        serviceMethod.encoder(),
        methodCall,
        null,
        invoker::invoke,
        false,
        false);

      stream.handler(dispatcher);
      stream.exceptionHandler(dispatcher::handleException);

      stream.init(headers, EventBusGrpcCodec.decodeBody(message.body()));
    }

    private <Req, Resp> void dispatchStreaming(Message<Object> message, ServiceMethod<Req, Resp> serviceMethod, WireFormat wireFormat) {
      String clientAddress = message.headers().get(EventBusHeaders.CLIENT_ADDRESS);
      String clientStreamIdHeader = message.headers().get(EventBusHeaders.STREAM_ID);
      if (clientAddress == null || clientStreamIdHeader == null) {
        message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.CLIENT_ADDRESS + "' or '" + EventBusHeaders.STREAM_ID + "' header");
        return;
      }

      long streamId;
      try {
        streamId = Long.parseLong(clientStreamIdHeader);
      } catch (NumberFormatException e) {
        message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Invalid '" + EventBusHeaders.STREAM_ID + "' header: " + clientStreamIdHeader);
        return;
      }

      int window = EventBusGrpcStreamBase.DEFAULT_WINDOW;
      long remoteTimeout = remoteTimeout(message.headers().get(EventBusHeaders.PING_TIMEOUT));

      MultiMap headers = MultiMap.caseInsensitiveMultiMap();
      EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);

      context().runOnContext(v -> {
        EventBusGrpcEndpoint.StreamRegistration registration = createStream(streamId);
        EventBusGrpcServerStreamingCall stream = new EventBusGrpcServerStreamingCall(
          context(),
          eventBus(),
          registration,
          clientAddress,
          wireFormat,
          "identity",
          window
        );

        registration.bind(stream, clientAddress, remoteTimeout);

        ServiceMethodInvoker<Req, Resp> invoker;
        try {
          invoker = service.invoker(serviceMethod);
        } catch (Exception e) {
          throw new UnsupportedOperationException("Handle me");
        }

        GrpcMethodCall methodCall = new GrpcMethodCall(serviceMethod.serviceName().pathOf(serviceMethod.methodName()));

        GrpcDispatcher<Req, Resp> dispatcher = new GrpcDispatcher<>(
          stream,
          context(),
          null,
          wireFormat,
          serviceMethod.decoder(),
          serviceMethod.encoder(),
          methodCall,
          null,
          invoker::invoke,
          false,
          false);


        stream.handler(dispatcher);
        stream.exceptionHandler(dispatcher::handleException);

        DeliveryOptions replyOptions = new DeliveryOptions()
          .addHeader(EventBusHeaders.SERVER_ADDRESS, address())
          .addHeader(EventBusHeaders.INITIAL_WINDOW, Integer.toString(window));

        message.reply(Buffer.buffer(), replyOptions);

        stream.init(headers);
      });
    }
  }
}
