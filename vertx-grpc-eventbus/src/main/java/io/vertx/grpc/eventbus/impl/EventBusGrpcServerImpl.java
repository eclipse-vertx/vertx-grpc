package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.grpc.common.*;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.ServiceMethodInvoker;
import io.vertx.grpc.server.impl.GrpcServerRequestImpl;
import io.vertx.grpc.server.impl.GrpcServerResponseImpl;

import java.util.*;
import java.util.stream.Collectors;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;

public class EventBusGrpcServerImpl implements EventBusGrpcServer {

  private static final Logger log = LoggerFactory.getLogger(EventBusGrpcServer.class);

  private final Vertx vertx;
  private final EventBus eventBus;
  private final Map<String, ServiceConsumer> consumers = new HashMap<>();

  public EventBusGrpcServerImpl(Vertx vertx, EventBus eventBus) {
    this.vertx = vertx;
    this.eventBus = eventBus;
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
    Descriptors.ServiceDescriptor descriptor1 = service.descriptor();
    if (descriptor1 != null) {
      for (Descriptors.MethodDescriptor descriptor : descriptor1.getMethods()) {
        if (descriptor.isClientStreaming() || descriptor.isServerStreaming()) {
//        streaming.add(descriptor.getName());
          log.warn("Streaming method " + serviceFqn + "/" + descriptor.getName() + " is not supported over the event bus transport, calls will be rejected with UNIMPLEMENTED");
        }
      }
    }

    Adapter adapter = new Adapter(serviceFqn, service);
    MessageConsumer<Object> consumer = eventBus.consumer(serviceFqn, adapter);
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
  public void close(Completable<Void> completion) {
    List<Future<Void>> futures = new ArrayList<>();
    for (ServiceConsumer consumer : consumers.values()) {
      futures.add(consumer.consumer.unregister());
      futures.add(consumer.service.close());
    }
    consumers.clear();
    Future.all(futures).<Void> mapEmpty().onComplete(completion);
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
      if (message.headers().get(EventBusHeaders.WIRE_FORMAT) == null) {
        message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.WIRE_FORMAT + "' header");
        return;
      }
      List<ServiceMethod<?, ?>> methods = service.methods();
      ServiceMethod<?, ?> serviceMethod = null;
      for (ServiceMethod<?, ?> candidate : methods) {
        if (candidate.methodName().equals(methodName)) {
          serviceMethod = candidate;
          break;
        }
      }
      if (serviceMethod == null) {
        String reason = "Method not found: " + methodName;

        // TODO: support streaming this depends on the event bus transport supporting streaming first
        // See https://github.com/eclipse-vertx/vert.x/pull/4712
        for (ServiceConsumer consumer : consumers.values()) {
          if (consumer.service.name().fullyQualifiedName().equals(serviceFqn)) {
            Descriptors.ServiceDescriptor desc = consumer.service.descriptor();
            if (desc != null) {
              Descriptors.MethodDescriptor methodDesc = desc.findMethodByName(methodName);
              if (methodDesc != null && (methodDesc.isClientStreaming() || methodDesc.isServerStreaming())) {
                reason = "Streaming method not supported over event bus transport: " + methodName;
              }
            }
            break;
          }
        }

        message.fail(GrpcStatus.UNIMPLEMENTED.code, reason);
        return;
      }
      dispatch(message, serviceMethod);
    }

      private <Req, Resp> void dispatch(Message<Object> message, ServiceMethod<Req, Resp> serviceMethod) {
        ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
        WireFormat wireFormat = WireFormat.valueOf(message.headers().get(EventBusHeaders.WIRE_FORMAT));

        Buffer payload = EventBusGrpcBody.asBuffer(message.body());

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(HEADER_PREFIX, message.headers(), headers);

        EventBusGrpcServerStream stream = new EventBusGrpcServerStream(context, message, wireFormat);
        GrpcMethodCall methodCall = new GrpcMethodCall(serviceMethod.serviceName().pathOf(serviceMethod.methodName()));
        GrpcServerRequestImpl<Req, Resp> request = new GrpcServerRequestImpl<>(
          context,
          headers,
          null,
          wireFormat,
          stream,
          null,
          "identity",
          serviceMethod.decoder(),
          methodCall
        );

        GrpcMessage grpcMessage = GrpcMessage.message("identity", wireFormat, payload);
        GrpcServerResponseImpl<Req, Resp> response = new GrpcServerResponseImpl<>(context, request, stream, null, serviceMethod.encoder());

        response.format(wireFormat);
        request.init(response, false);
        stream.endHandler(v -> request.handleEnd());

        try {
          ServiceMethodInvoker<Req, Resp> invoker = service.invoker(serviceMethod);
          invoker.invoke(request);
        } catch (Exception e) {
          response.fail(e);
          return;
        }

        request.handleMessage(grpcMessage);
        stream.emitEnd();
      }
  }
}
