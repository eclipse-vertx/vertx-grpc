package io.vertx.grpc.eventbus.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMethodCall;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.EventBusHeaders;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerService;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.impl.GrpcServerRequestImpl;
import io.vertx.grpc.server.impl.GrpcServerResponseImpl;

import java.util.*;

public class EventBusGrpcServerImpl implements EventBusGrpcServer {

  private static final Logger log = LoggerFactory.getLogger(EventBusGrpcServer.class);

  private final Vertx vertx;
  private final EventBus eventBus;
  private final Map<String, Map<String, MethodHandler<?, ?>>> handlers = new HashMap<>();
  private final Map<String, MessageConsumer<Object>> consumers = new HashMap<>();
  private final List<Service> services = new ArrayList<>();

  public EventBusGrpcServerImpl(Vertx vertx, EventBus eventBus) {
    this.vertx = vertx;
    this.eventBus = eventBus;
  }

  @Override
  public <Req, Resp> GrpcServerService callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    String serviceFqn = serviceMethod.serviceName().fullyQualifiedName();
    String methodName = serviceMethod.methodName();

    if (handler != null) {
      handlers.computeIfAbsent(serviceFqn, k -> new HashMap<>()).put(methodName, new MethodHandler<>(serviceMethod, handler));
      ensureConsumer(serviceFqn);
    } else {
      Map<String, MethodHandler<?, ?>> methodHandlers = handlers.get(serviceFqn);
      if (methodHandlers != null) {
        methodHandlers.remove(methodName);
        if (methodHandlers.isEmpty()) {
          handlers.remove(serviceFqn);
          removeConsumer(serviceFqn);
        }
      }
    }

    return this;
  }

  @Override
  public EventBusGrpcServer addService(Service service) {
    for (Service s : services) {
      if (s.name().equals(service.name())) {
        throw new IllegalStateException("Duplicated name: " + service.name().name());
      }
    }

    String serviceFqn = service.name().fullyQualifiedName();
    List<String> streaming = new ArrayList<>();
    for (Descriptors.MethodDescriptor descriptor : service.descriptor().getMethods()) {
      if (descriptor.isClientStreaming() || descriptor.isServerStreaming()) {
        streaming.add(descriptor.getName());
        log.warn("Streaming method " + serviceFqn + "/" + descriptor.getName() + " is not supported over the event bus transport, calls will be rejected with UNIMPLEMENTED");
      }
    }

    services.add(service);
    service.bind(this);

    if (!streaming.isEmpty()) {
      Map<String, MethodHandler<?, ?>> methodHandlers = handlers.get(serviceFqn);
      if (methodHandlers != null) {
        streaming.forEach(methodHandlers::remove);
      }
      ensureConsumer(serviceFqn);
    }

    return this;
  }

  @Override
  public List<Service> services() {
    return Collections.unmodifiableList(services);
  }

  @Override
  public void close(Completable<Void> completion) {
    List<Future<Void>> futures = new ArrayList<>();
    for (MessageConsumer<Object> consumer : consumers.values()) {
      futures.add(consumer.unregister());
    }

    consumers.clear();
    handlers.clear();
    for (Service service : services) {
      futures.add(service.close());
    }

    services.clear();
    Future.all(futures).<Void> mapEmpty().onComplete(completion);
  }

  private void ensureConsumer(String serviceFqn) {
    if (!consumers.containsKey(serviceFqn)) {
      MessageConsumer<Object> consumer = eventBus.consumer(serviceFqn, msg -> handleMessage(serviceFqn, msg));
      consumers.put(serviceFqn, consumer);
    }
  }

  private void removeConsumer(String serviceFqn) {
    MessageConsumer<Object> consumer = consumers.remove(serviceFqn);
    if (consumer != null) {
      consumer.unregister();
    }
  }

  private void handleMessage(String serviceFqn, Message<Object> message) {
    String methodName = message.headers().get(EventBusHeaders.ACTION);
    if (methodName == null) {
      message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.ACTION + "' header");
      return;
    }

    if (message.headers().get(EventBusHeaders.WIRE_FORMAT) == null) {
      message.fail(GrpcStatus.INVALID_ARGUMENT.code, "Missing '" + EventBusHeaders.WIRE_FORMAT + "' header");
      return;
    }

    Map<String, MethodHandler<?, ?>> methodHandlers = handlers.get(serviceFqn);
    MethodHandler<?, ?> handler = methodHandlers != null ? methodHandlers.get(methodName) : null;
    if (handler == null) {
      String reason = "Method not found: " + methodName;

      // TODO: support streaming this depends on the event bus transport supporting streaming first
      // See https://github.com/eclipse-vertx/vert.x/pull/4712
      for (Service service : services) {
        if (service.name().fullyQualifiedName().equals(serviceFqn)) {
          Descriptors.MethodDescriptor descriptor = service.descriptor().findMethodByName(methodName);
          if (descriptor != null && (descriptor.isClientStreaming() || descriptor.isServerStreaming())) {
            reason = "Streaming method not supported over event bus transport: " + methodName;
          }
          break;
        }
      }

      message.fail(GrpcStatus.UNIMPLEMENTED.code, reason);
      return;
    }

    dispatch(message, handler);
  }

  private <Req, Resp> void dispatch(Message<Object> message, MethodHandler<Req, Resp> handler) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    WireFormat wireFormat = WireFormat.valueOf(message.headers().get(EventBusHeaders.WIRE_FORMAT));

    Buffer payload = EventBusGrpcBody.asBuffer(message.body());

    EventBusGrpcServerStream stream = new EventBusGrpcServerStream(context, message, wireFormat);
    GrpcMethodCall methodCall = new GrpcMethodCall(handler.serviceMethod.serviceName().pathOf(handler.serviceMethod.methodName()));
    GrpcServerRequestImpl<Req, Resp> request = new GrpcServerRequestImpl<>(
      context,
      message.headers(),
      null,
      wireFormat,
      stream,
      null,
      "identity",
      handler.serviceMethod.decoder(),
      methodCall
    );

    GrpcMessage grpcMessage = GrpcMessage.message("identity", wireFormat, payload);
    GrpcServerResponseImpl<Req, Resp> response = new GrpcServerResponseImpl<>(context, request, stream, null, handler.serviceMethod.encoder());

    response.format(wireFormat);
    request.init(response, false);
    stream.endHandler(v -> request.handleEnd());

    try {
      handler.handler.handle(request);
    } catch (Exception e) {
      response.fail(e);
      return;
    }

    request.handleMessage(grpcMessage);
    stream.emitEnd();
  }

  private static class MethodHandler<Req, Resp> {
    final ServiceMethod<Req, Resp> serviceMethod;
    final Handler<GrpcServerRequest<Req, Resp>> handler;

    MethodHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.serviceMethod = serviceMethod;
      this.handler = handler;
    }
  }
}
