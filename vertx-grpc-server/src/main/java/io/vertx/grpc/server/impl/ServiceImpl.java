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

  private Descriptors.ServiceDescriptor descriptor;
  private final List<ServiceMethodData<?, ?>> handlers = new LinkedList<>();

  public ServiceImpl(ServiceName serviceName) {
    this.serviceName = serviceName;
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
  public Service descriptor(Descriptors.ServiceDescriptor serviceDescriptor) {
    this.descriptor = serviceDescriptor;
    return this;
  }

  @Override
  public <Req, Resp> Service callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
    handlers.add(new ServiceMethodData<>(serviceMethod, handler));
    return this;
  }

  @Override
  public Service bind(GrpcServer server) {
    handlers.forEach(h -> h.bind(server));
    return this;
  }

  private static final class ServiceMethodData<Req, Resp> {
    private final ServiceMethod<Req, Resp> serviceMethod;
    private final Handler<GrpcServerRequest<Req, Resp>> handler;

    private ServiceMethodData(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
      this.serviceMethod = serviceMethod;
      this.handler = handler;
    }

    private ServiceMethod<Req, Resp> serviceMethod() {
      return serviceMethod;
    }

    private Handler<GrpcServerRequest<Req, Resp>> handler() {
      return handler;
    }

    private void bind(GrpcServer server) {
      server.callHandler(serviceMethod, handler);
    }
  }
}
