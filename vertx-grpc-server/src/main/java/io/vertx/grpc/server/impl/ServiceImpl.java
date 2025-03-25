package io.vertx.grpc.server.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Handler;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;

import java.util.LinkedList;
import java.util.List;

public class ServiceImpl implements Service {

  private final ServiceName serviceName;

  private final Descriptors.ServiceDescriptor descriptor;
  private final List<Service.ServiceMethodData<?, ?>> handlers = new LinkedList<>();

  public ServiceImpl(ServiceName serviceName, Descriptors.ServiceDescriptor descriptor) {
    this.serviceName = serviceName;
    this.descriptor = descriptor;
  }

  @Override
  public ServiceName name() {
    return serviceName;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public <Req, Resp> Service callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    handlers.add(new Service.ServiceMethodData<>(serviceMethod, handler));
    return this;
  }

  @Override
  public Service bind(GrpcServer server) {
    handlers.forEach(h -> h.bind(server));
    return this;
  }
}
