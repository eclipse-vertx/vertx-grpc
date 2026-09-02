package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.grpc.common.*;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusGrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.impl.GrpcDispatcher;
import io.vertx.grpc.server.impl.GrpcMethodCall;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class EventBusGrpcServerEndpoint extends EventBusGrpcEndpoint implements EventBusGrpcServer {

  public static Future<EventBusGrpcServer> create(VertxInternal vertx, EventBusGrpcServerOptions options) {
    ContextInternal consumerContext = vertx.getOrCreateContext();
    EventBusGrpcServerEndpoint server = new CleanableEventBusGrpcServer(consumerContext, options);
    return server
      .bind()
      .map(server);
  }

  private final Set<WireFormat> acceptedWireFormats;
  private final long maxPingTimeout;
  private final ContextInternal consumerContext;
  private final AtomicReference<Map<String, ServiceConsumer>> consumersRef;

  EventBusGrpcServerEndpoint(ContextInternal consumerContext, EventBusGrpcServerOptions options) {
    super(Utils.eventLoopCtx(consumerContext),  options.getWireFormat(), "grpc.eb.server.", options.getCleanerPeriod().toMillis(),
      0L, options.getInitialWindowSize());
    this.consumerContext = consumerContext;
    this.acceptedWireFormats = new LinkedHashSet<>(options.getEnabledFormats());
    this.maxPingTimeout = options.getMaxPingTimeout().toMillis();
    this.consumersRef = new AtomicReference<>(new HashMap<>());
  }

  Future<Void> bind() {
    Promise<Void> completion = consumerContext.promise();
    bind(completion);
    return completion.future();
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

  @Override
  public <Req, Resp> EventBusGrpcServerEndpoint callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {

    String serviceFqn = serviceMethod.serviceName().fullyQualifiedName();
    ServiceConsumer consumer = consumersRef.get().get(serviceFqn);

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
        simpleService.handlers.put(methodName, new MethodHandler<>(handler));
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

    Map<String, ServiceConsumer> consumers;
    Map<String, ServiceConsumer> copy;
    do {
      consumers = consumersRef.get();
      for (ServiceConsumer consumer : consumers.values()) {
        if (consumer.service.name().equals(service.name())) {
          throw new IllegalStateException("Duplicated name: " + service.name().name());
        }
      }
      String serviceFqn = service.name().fullyQualifiedName();
      MessageConsumer<Object> consumer = consumer(serviceFqn, new Adapter(service));
      copy = new HashMap<>(consumers);
      copy.put(serviceFqn, new ServiceConsumer(consumer, service));
    }
    while (!consumersRef.compareAndSet(consumers, copy));

    return this;
  }

  @Override
  public List<Service> services() {
    return consumersRef
      .get()
      .values()
      .stream()
      .map(sc -> sc.service)
      .collect(Collectors.toList());
  }

  @Override
  protected Future<Void> handleClose() {
    List<Future<Void>> futures = new ArrayList<>();
    Map<String, ServiceConsumer> consumers = consumersRef.getAndSet(Collections.emptyMap());
    for (ServiceConsumer consumer : consumers.values()) {
      futures.add(consumer.consumer.unregister());
      futures.add(consumer.service.close());
    }
    return Future
      .join(futures)
      .<Void>mapEmpty();
  }

  private class MethodHandler<Req, Resp> implements Handler<GrpcServerRequest<Req, Resp>> {

    private final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodHandler(Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.handler = handler;
    }

    @Override
    public void handle(GrpcServerRequest<Req, Resp> request) {
      assert consumerContext.inThread();
      ContextInternal prev = consumerContext.beginDispatch();
      try {
        handler.handle(request);
      } finally {
        consumerContext.endDispatch(prev);
      }
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
    public <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
      MethodHandler<?, ?> methodHandler = handlers.get(method.methodName());
      if (methodHandler != null) {
        return (Handler) methodHandler;
      } else {
        return Service.super.handler(method);
      }
    }
  }

  private class Adapter implements Handler<Message<Object>> {

    private final Service service;

    public Adapter(Service service) {
      this.service = service;
    }

    @Override
    public void handle(Message<Object> message) {

      boolean isServiceProxy;
      WireFormat wireFormat;
      long streamId;
      String methodName = message.headers().get(EventBusHeaders.STREAM_METHOD_NAME);
      if (methodName == null) {
        methodName = message.headers().get(EventBusHeaders.SERVICE_PROXY_ACTION);
        if (methodName == null) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.SERVICE_PROXY_ACTION + "' header");
          return;
        }
        isServiceProxy = true;
        wireFormat = WireFormat.JSON;
        streamId = nextStreamId();
      } else {
        isServiceProxy = false;
        String wireFormatName = message.headers().get(EventBusHeaders.STREAM_WIRE_FORMAT);
        if (ProtobufWireFormat.NAME.equals(wireFormatName)) {
          wireFormat = WireFormat.PROTOBUF;
        } else if (JsonWireFormat.NAME.equals(wireFormatName)) {
          wireFormat = WireFormat.JSON;
        } else {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Unknown wire format: " + wireFormatName);
          return;
        }
        String clientStreamIdHeader = message.headers().get(EventBusHeaders.STREAM_ID);
        if (clientStreamIdHeader == null) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.STREAM_ID + "' header");
          return;
        } else {
          try {
            streamId = Long.parseLong(clientStreamIdHeader);
          } catch (NumberFormatException e) {
            message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Invalid '" + EventBusHeaders.STREAM_ID + "' header: " + clientStreamIdHeader);
            return;
          }
        }
      }

      if (!acceptedWireFormats.contains(wireFormat)) {
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

      boolean clientStreaming = message.body() == null;
      boolean serverStreaming;
      if (clientStreaming) {
        serverStreaming = message.headers().get(EventBusHeaders.STREAM_INITIAL_WINDOW) != null;
      } else {
        serverStreaming = message.headers().get(EventBusHeaders.ENDPOINT_ADDRESS) != null;
      }

      if (serviceMethod == null) {
        message.fail(GrpcStatus.UNIMPLEMENTED.code, "Method not found: " + methodName);
      } else if (isServiceProxy && (clientStreaming || serverStreaming)) {
        message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Unsupported service proxy action");
      } else {
        dispatchStreaming(message, serviceMethod, clientStreaming, serverStreaming, streamId, wireFormat);
      }
    }

    private <Req, Resp> void dispatchStreaming(Message<Object> message,
                                               ServiceMethod<Req, Resp> serviceMethod,
                                               boolean clientStreaming,
                                               boolean serverStreaming,
                                               long streamId,
                                               WireFormat wireFormat) {

      boolean needClientAddress = serverStreaming || clientStreaming;
      String clientAddress = message.headers().get(EventBusHeaders.ENDPOINT_ADDRESS);
      String s = message.headers().get(EventBusHeaders.ENDPOINT_WIRE_FORMAT);

      WireFormat clientFormat;
      if (needClientAddress) {
        if (clientAddress == null) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.ENDPOINT_ADDRESS + "' header");
          return;
        }
        if (s == null) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.ENDPOINT_WIRE_FORMAT + "' header");
          return;
        }
        switch (s) {
          case "json":
            clientFormat = WireFormat.JSON;
            break;
          case "proto":
            clientFormat = WireFormat.PROTOBUF;
            break;
          default:
            message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Invalid '" + EventBusHeaders.ENDPOINT_WIRE_FORMAT + "' header");
            return;
        }
      } else {
        if (clientAddress != null) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Invalid '" + EventBusHeaders.ENDPOINT_ADDRESS + "' header");
          return;
        }
        if (s != null) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Invalid '" + EventBusHeaders.ENDPOINT_WIRE_FORMAT + "' header");
          return;
        }
        clientFormat = null;
      }

      int initialOutboundWindowSize;
      if (serverStreaming) {
        String initialWindowHeader = message.headers().get(EventBusHeaders.STREAM_INITIAL_WINDOW);
        if (initialWindowHeader == null) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.STREAM_INITIAL_WINDOW + "' header");
          return;
        }
        try {
          initialOutboundWindowSize = Integer.parseInt(initialWindowHeader);
        } catch (NumberFormatException e) {
          message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Invalid '" + EventBusHeaders.STREAM_INITIAL_WINDOW + "' header");
          return;
        }
      } else {
        initialOutboundWindowSize = EventBusGrpcClientOptions.DEFAULT_INITIAL_WINDOW_SIZE;
      }

      long remoteTimeout = remoteTimeout(message.headers().get(EventBusHeaders.ENDPOINT_PING_TIMEOUT));

      EventBusGrpcServerStream stream = new EventBusGrpcServerStream(
        EventBusGrpcServerEndpoint.this,
        streamId,
        consumerContext,
        !serverStreaming,
        !clientStreaming,
        wireFormat,
        "identity",
        initialWindowSize,
        initialOutboundWindowSize
      );

      stream.registerStream();
      if (clientAddress != null) {
        stream.registerRemoteEndpoint(clientAddress, remoteTimeout, clientFormat);
      }

      Handler<GrpcServerRequest<Req, Resp>> invoker;
      try {
        invoker = service.handler(serviceMethod);
      } catch (Exception e) {
        throw new UnsupportedOperationException("Handle me");
      }

      GrpcMethodCall<Req, Resp> methodCall = new GrpcMethodCall<>(
        serviceMethod.serviceName().pathOf(serviceMethod.methodName()),
        stream, serviceMethod.decoder(), serviceMethod.encoder());

      GrpcDispatcher<Req, Resp> dispatcher = new GrpcDispatcher<>(
        consumerContext,
        methodCall,
        null,
        invoker,
        false,
        false);

      stream.handler(dispatcher);
      stream.exceptionHandler(dispatcher::handleException);
      stream.errorHandler(dispatcher::handleError);
      stream.endHandler(dispatcher::handleEnd);

      stream.handleConnect(message);
    }
  }
}
