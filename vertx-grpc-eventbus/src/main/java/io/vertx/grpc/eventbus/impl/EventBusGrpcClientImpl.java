package io.vertx.grpc.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcClientOptions;

import java.util.concurrent.atomic.AtomicInteger;

public class EventBusGrpcClientImpl extends EventBusGrpcEndpoint implements EventBusGrpcClient {

  private final WireFormat wireFormat;
  private final AtomicInteger sequence = new AtomicInteger();

  private EventBusGrpcClientImpl(ContextInternal producerContext, EventBusGrpcClientOptions options) {
    super(producerContext, "grpc.eb.client.", options.getWireFormat(), options.getPingInterval().toMillis(), pingTimeout(options));
    this.wireFormat = options.getWireFormat();
  }

  private static long pingTimeout(EventBusGrpcClientOptions options) {
    if (options.getPingTimeout().compareTo(options.getPingInterval()) <= 0) {
      throw new IllegalArgumentException("pingTimeout (" + options.getPingTimeout() + ") must be greater than pingInterval (" + options.getPingInterval() + ")");
    }
    return options.getPingTimeout().toMillis();
  }

  StreamRegistration createStream() {
    long id = ((long)id()) << 32 | sequence.getAndIncrement();
    return createStream(id);
  }

  public static Future<EventBusGrpcClient> create(Vertx vertx, EventBusGrpcClientOptions options) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal producerContext = Utils.eventLoopCtx(context);
    EventBusGrpcClientImpl client = new EventBusGrpcClientImpl(producerContext, options);
    PromiseInternal<Void> promise = context.promise();
    client.bind(promise);
    return promise
      .future()
      .map(client);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> invoker(ServiceMethod<Resp, Req> method) {
    return request(method);
  }

  @Override
  public <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method) {
    ContextInternal consumerContext = vertx.getOrCreateContext();
    EventBusGrpcClientInvoker invoker = new EventBusGrpcClientInvoker(consumerContext, this, !method.clientStreaming(), !method.serverStreaming());
    GrpcClientRequestImpl<Req, Resp> request = new GrpcClientRequestImpl<>(
      consumerContext,
      invoker,
      false,
      method.encoder(),
      method.decoder()
    );
    request.serviceName(method.serviceName());
    request.methodName(method.methodName());
    request.format(wireFormat);
    return consumerContext.succeededFuture(request);
  }

  @Override
  public Future<Void> close() {
    return closeStreams();
  }
}
